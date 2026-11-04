package kr.dothome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

@Entity @Table(name="member_expression")
class MemberExpression extends Row {
    @Column(nullable=false,length=36) String memberId;
    @Column(nullable=false,length=24) String fieldName;
    @Column(length=200) String expressionValue;
    @Column(nullable=false) Instant updatedAt;
    Instant expiresAt;
    long version;
}

@Entity @Table(name="expression_command")
class ExpressionCommand extends Row {
    @Column(nullable=false,length=36) String memberId;
    @Column(nullable=false,length=80) String requestId;
    @Column(nullable=false,length=64) String bodyHash;
    @Column(nullable=false,columnDefinition="TEXT") String responseJson;
    @Column(nullable=false) Instant expiresAt;
}

interface MemberExpressions extends JpaRepository<MemberExpression,String> {
    List<MemberExpression> findByMemberIdIn(Collection<String> memberIds);
}
interface ExpressionCommands extends JpaRepository<ExpressionCommand,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ExpressionCommand> findByMemberIdAndRequestId(String memberId,String requestId);
    @Modifying @Query("delete from ExpressionCommand c where c.expiresAt<=:now")
    void purge(Instant now);
}

record ExpressionValue(String value,Instant updatedAt,Instant expiresAt,long version) {
    boolean currentAt(Instant now){return value!=null && expiresAt!=null && expiresAt.isAfter(now);}
    ExpressionValue at(Instant now){return currentAt(now)?this:new ExpressionValue(null,updatedAt,expiresAt,version);}
}
record SharingValue(boolean enabled,long version) {}
record SharedPlace(String status,String place,String lastPlace,Instant sharedAt,Instant expiresAt) {}
record ExpressionState(Map<String,ExpressionValue> expressions,ExpressionValue place,SharingValue sharing,long revision,Instant serverTime) {}
record ExpressionPatch(@NotBlank @Size(max=80) String requestId,@NotNull @Size(max=7) Map<String,JsonNode> changes) {}

@Component
class ExpressionStore {
    private record Field(int maxLength,int defaultTtl,Set<String> values){}
    private record Change(String value,long version,Integer ttlMinutes){}
    private static final Map<String,Field> FIELDS=Map.of(
        "thought",new Field(60,120,Set.of()),
        "food",new Field(30,360,Set.of()),
        "mood",new Field(0,240,Set.of("CALM","HAPPY","EXCITED","LONELY","HURT","IRRITATED","WORRIED","TIRED")),
        "physical",new Field(0,240,Set.of("OK","SLEEPY","FATIGUED","UNWELL","RESTING")),
        "availability",new Field(0,240,Set.of("TALK","ENCOURAGE_ONLY","QUIET"))
    );

    private static final Field PLACE=new Field(0,120,Set.of("HOME","OFFICE","SCHOOL","TRANSIT","MEAL","EXERCISE","ERRAND","TRAVEL","OUTSIDE"));

    final MemberExpressions rows;
    final ExpressionCommands commands;
    final ObjectMapper json;
    ExpressionStore(MemberExpressions rows,ExpressionCommands commands,ObjectMapper json){
        this.rows=rows;this.commands=commands;this.json=json;
    }
    Map<String,Map<String,MemberExpression>> load(Collection<Member> members){
        var result=new HashMap<String,Map<String,MemberExpression>>();
        if(members.isEmpty())return result;
        for(var row:rows.findByMemberIdIn(members.stream().map(m->m.id).toList()))
            result.computeIfAbsent(row.memberId,k->new HashMap<>()).put(row.fieldName,row);
        return result;
    }
    Map<String,MemberExpression> load(Member member){
        return load(List.of(member)).getOrDefault(member.id,new HashMap<>());
    }
    private ExpressionValue read(Member member,Map<String,MemberExpression> saved,String field){
        var row=saved.get(field);
        if(row!=null)return new ExpressionValue(row.expressionValue,row.updatedAt,row.expiresAt,row.version);
        if(field.equals("sharing"))return new ExpressionValue(
            Boolean.toString(member.statusExpiresAt!=null),null,null,0);
        if(field.equals("place")&&member.statusExpiresAt!=null)
            return new ExpressionValue(member.place.toUpperCase(Locale.ROOT),member.statusUpdatedAt,member.statusExpiresAt,0);
        String legacy=switch(field){case "thought"->member.thought;case "food"->member.food;case "mood"->member.mood;default->"";};
        return legacy.isBlank()?new ExpressionValue(null,null,null,0)
            :new ExpressionValue(legacy,member.statusUpdatedAt,member.statusExpiresAt,0);
    }
    Map<String,ExpressionValue> state(Member member,Map<String,MemberExpression> saved,Instant now,boolean includeEmpty){
        var result=new TreeMap<String,ExpressionValue>();
        for(String field:FIELDS.keySet()){
            var value=read(member,saved,field);
            if(includeEmpty || value.currentAt(now))result.put(field,value.at(now));
        }
        return result;
    }
    SharingValue sharing(Member member,Map<String,MemberExpression> saved){
        var value=read(member,saved,"sharing");
        return new SharingValue(Boolean.parseBoolean(value.value()),value.version());
    }
    SharedPlace publicPlace(Member member,Map<String,MemberExpression> saved,Instant now){
        if(!sharing(member,saved).enabled())return new SharedPlace("UNSHARED",null,null,null,null);
        var place=read(member,saved,"place");
        if(place.currentAt(now))return new SharedPlace("MANUAL",place.value(),null,place.updatedAt(),place.expiresAt());
        return new SharedPlace("UNKNOWN",null,place.value(),place.updatedAt(),place.expiresAt());
    }
    ExpressionState response(Household house,Member member,Map<String,MemberExpression> saved,Instant now){
        return new ExpressionState(state(member,saved,now,true),read(member,saved,"place").at(now),
            sharing(member,saved),house.revision,now);
    }
    private static Change parse(String name,JsonNode input){
        boolean sharing=name.equals("sharing");
        String valueKey=sharing?"enabled":"value";
        var field=name.equals("place")?PLACE:FIELDS.get(name);
        if(!sharing&&field==null||input==null||!input.isObject()||!input.has(valueKey)||!input.has("version")||
            input.properties().stream().anyMatch(e->!(sharing?Set.of("enabled","version"):Set.of("value","version","ttlMinutes")).contains(e.getKey())))
            throw ApiException.invalid("수정할 표현과 버전을 확인해 주세요.");
        var version=input.get("version");
        var value=input.get(valueKey);
        if(!version.isIntegralNumber()||!version.canConvertToLong()||version.asLong()<0)
            throw ApiException.invalid("표현 버전을 확인해 주세요.");
        if(sharing){
            if(!value.isBoolean())throw ApiException.invalid("장소 공유 설정을 확인해 주세요.");
            return new Change(value.asText(),version.asLong(),null);
        }
        if(!(value.isNull()||value.isTextual()))throw ApiException.invalid("표현 값을 확인해 주세요.");
        String normalized=value.isNull()?null:value.asText().strip();
        if(normalized!=null && normalized.isEmpty())normalized=null;
        if(normalized!=null){
            boolean valid=field.values().isEmpty()
                ? normalized.codePointCount(0,normalized.length())<=field.maxLength()
                : field.values().contains(normalized);
            if(!valid)throw ApiException.invalid("표현의 길이나 선택값을 확인해 주세요.");
        }
        var ttl=input.get("ttlMinutes");
        boolean place=name.equals("place");
        if(ttl!=null&&(!ttl.isIntegralNumber()||!ttl.canConvertToInt()||
            ttl.asInt()<(place?15:30)||ttl.asInt()>(place?120:1440)||normalized==null))
            throw ApiException.invalid(place?"장소 공유 시간은 15~120분이에요.":"표현 유지 시간은 30분~24시간이에요.");
        return new Change(normalized,version.asLong(),ttl==null?null:ttl.asInt());
    }
    private boolean change(Member member,Map<String,MemberExpression> saved,String name,Change change,Instant now){
        var previous=read(member,saved,name);
        if(change.version()!=previous.version())throw ApiException.conflict("다른 화면에서 내 표현이 변경됐어요.");
        boolean setting=name.equals("sharing");
        var current=setting?previous:previous.at(now);
        boolean unchanged=Objects.equals(change.value(),current.value()) &&
            (change.value()!=null || previous.value()==null);
        if(unchanged && change.ttlMinutes()==null)return false;
        var row=saved.computeIfAbsent(name,field->{
            var added=new MemberExpression();added.memberId=member.id;added.fieldName=field;return added;
        });
        row.expressionValue=change.value();
        row.updatedAt=now;
        row.expiresAt=setting||change.value()==null?null:now.plus(
            change.ttlMinutes()!=null?change.ttlMinutes():name.equals("place")?PLACE.defaultTtl():FIELDS.get(name).defaultTtl(),
            ChronoUnit.MINUTES);
        row.version=previous.version()+1;
        rows.save(row);
        return true;
    }
    boolean legacy(Member member,Map<String,MemberExpression> saved,String field,String value,Integer ttl,Instant now){
        if(value==null)return false;
        String next=value.strip();
        if(next.isEmpty())next=null;
        var previous=read(member,saved,field);
        if(Objects.equals(next,previous.at(now).value()))return false;
        return change(member,saved,field,new Change(next,previous.version(),next==null?null:ttl),now);
    }
    boolean legacyPlace(Member member,Map<String,MemberExpression> saved,String place,Integer ttl,Instant now){
        if(place==null)return false;
        String value=place.toUpperCase(Locale.ROOT);
        var old=read(member,saved,"place");
        if(Objects.equals(old.at(now).value(),value)&&sharing(member,saved).enabled())return false;
        if(ttl!=null&&ttl>120)throw ApiException.invalid("장소를 공유하는 시간은 최대 120분이에요.");
        change(member,saved,"sharing",new Change("true",sharing(member,saved).version(),null),now);
        return change(member,saved,"place",new Change(value,old.version(),ttl),now);
    }
    ExpressionState patch(Household house,Member member,ExpressionPatch input,Consumer<String> placeChanged,Runnable changed){
        Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        var changes=new TreeMap<String,Change>();
        input.changes().forEach((field,value)->changes.put(field,parse(field,value)));
        String hash=HomeService.hash(json.writeValueAsString(changes));
        var receipt=commands.findByMemberIdAndRequestId(member.id,input.requestId()).orElse(null);
        if(receipt!=null&&receipt.expiresAt.isAfter(now)){
            if(!receipt.bodyHash.equals(hash))throw ApiException.conflict("동일 요청 번호의 내용이 달라요.");
            return json.readValue(receipt.responseJson,ExpressionState.class);
        }
        var saved=load(member);
        var sharing=changes.get("sharing");
        var place=changes.get("place");
        boolean mayShare=sharing==null?sharing(member,saved).enabled():Boolean.parseBoolean(sharing.value());
        if(place!=null&&place.value()!=null&&!mayShare)
            throw ApiException.invalid("장소를 공유하려면 먼저 공유를 허용해 주세요.");
        boolean updated=false;
        for(var entry:changes.entrySet()){
            boolean fieldChanged=change(member,saved,entry.getKey(),entry.getValue(),now);
            updated|=fieldChanged;
            if(fieldChanged&&entry.getKey().equals("place")&&entry.getValue().value()!=null)
                placeChanged.accept(entry.getValue().value());
        }
        if(sharing!=null&&!mayShare){
            var oldPlace=read(member,saved,"place");
            updated|=change(member,saved,"place",new Change(null,oldPlace.version(),null),now);
        }
        if(updated)changed.run();
        var result=response(house,member,saved,now);
        if(receipt==null){receipt=new ExpressionCommand();receipt.memberId=member.id;receipt.requestId=input.requestId();}
        receipt.bodyHash=hash;receipt.responseJson=json.writeValueAsString(result);receipt.expiresAt=now.plus(1,ChronoUnit.DAYS);
        commands.save(receipt);
        return result;
    }
    @Scheduled(fixedDelay=3_600_000) @Transactional
    public void purgeReceipts(){commands.purge(Instant.now());}
}

@RestController @RequestMapping("/api/households/{id}/me/state")
class ExpressionController {
    private final HomeService homes;
    ExpressionController(HomeService homes){this.homes=homes;}
    @GetMapping ExpressionState read(@PathVariable String id,Principal principal){return homes.expressions(id,principal.getName());}
    @PatchMapping ExpressionState patch(@PathVariable String id,Principal principal,@Valid @RequestBody ExpressionPatch input){
        return homes.expressions(id,principal.getName(),input);
    }
}
