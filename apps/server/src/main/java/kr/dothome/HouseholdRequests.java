package kr.dothome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

enum RequestKind { NOTICE, TASK, PROMISE }
enum RequestState { ACTIVE, OPEN, CLAIMED, DONE, CANCELLED, EXPIRED }

@Entity @Table(name="household_request")
class HouseholdRequest extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String authorMemberId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) RequestKind kind;
    @Column(nullable=false,length=80) String title;
    @Column(nullable=false,length=300) String body;
    @Column(nullable=false,columnDefinition="TEXT") String targetsJson;
    @Column(length=36) String assigneeMemberId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) RequestState state;
    @Column(nullable=false) Instant dueAt;
    @Column(length=36) String completedByMemberId;
    Instant completedAt;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    @Version long version;

    RequestState stateAt(Instant now){
        return switch(state){
            case ACTIVE,OPEN,CLAIMED -> dueAt.isAfter(now)?state:RequestState.EXPIRED;
            default -> state;
        };
    }
}

@Entity @Table(name="request_transition")
class RequestTransition extends Row {
    @Column(nullable=false,length=36) String requestId;
    @Column(nullable=false,length=36) String actorMemberId;
    @Enumerated(EnumType.STRING) @Column(length=16) RequestState previousState;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) RequestState nextState;
    @Column(length=200) String reason;
    @Column(nullable=false) Instant occurredAt;
}

@Entity @Table(name="request_command")
class RequestCommand extends Row {
    @Column(nullable=false,length=36) String memberId;
    @Column(nullable=false,length=64) String commandScope;
    @Column(nullable=false,length=80) String requestKey;
    @Column(nullable=false,length=64) String bodyHash;
    @Column(nullable=false,columnDefinition="TEXT") String responseJson;
    @Column(nullable=false) Instant expiresAt;
}

interface HouseholdRequests extends JpaRepository<HouseholdRequest,String> {
    Optional<HouseholdRequest> findByIdAndHouseholdId(String id,String householdId);
    @Query("""
        select r from HouseholdRequest r where r.householdId=:household
        and (r.state in :states and (r.state not in ('ACTIVE','OPEN','CLAIMED') or r.dueAt>:now)
            or :expired=true and r.state in ('ACTIVE','OPEN','CLAIMED') and r.dueAt<=:now)
        and (:first=true or r.createdAt<:before or (r.createdAt=:before and r.id<:beforeId))
        order by r.createdAt desc,r.id desc
        """)
    List<HouseholdRequest> page(String household,Collection<RequestState> states,boolean expired,Instant now,
        boolean first,Instant before,String beforeId,Pageable page);
}
interface RequestTransitions extends JpaRepository<RequestTransition,String> {
    List<RequestTransition> findByRequestIdOrderByOccurredAtAscIdAsc(String requestId);
}
interface RequestCommands extends JpaRepository<RequestCommand,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RequestCommand> findByMemberIdAndCommandScopeAndRequestKey(String memberId,String commandScope,String requestKey);
    @Modifying @Query("delete from RequestCommand c where c.expiresAt<=:now")
    void purge(Instant now);
}

record CreateRequest(@NotBlank @Size(max=80) String requestId,@NotNull RequestKind kind,
    @NotBlank String title,String body,List<@NotBlank @Size(max=36) String> targetMemberIds,Instant dueAt) {}
record RequestAction(@NotBlank @Size(max=80) String requestId,@NotNull @PositiveOrZero Long version,String reason) {}
record HouseholdRequestView(String id,RequestKind kind,String title,String body,String authorMemberId,
    List<String> targetMemberIds,String assigneeMemberId,RequestState state,Instant dueAt,String completedByMemberId,
    Instant completedAt,long version,Instant createdAt,Instant updatedAt) {}
record HouseholdRequestPage(List<HouseholdRequestView> items,String nextCursor) {}
record RequestHistoryItem(String actorMemberId,RequestState previousState,RequestState nextState,String reason,Instant occurredAt) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class HouseholdRequestService {
    private static final ZoneId HOUSE_TIME=ZoneId.of("Asia/Seoul");
    private static final Set<RequestState> ALL=EnumSet.allOf(RequestState.class);
    final HomeService homes;
    final Members members;
    final HouseholdRequests requests;
    final RequestTransitions history;
    final RequestCommands commands;
    final ObjectMapper json;
    HouseholdRequestService(HomeService homes,Members members,HouseholdRequests requests,RequestTransitions history,RequestCommands commands,ObjectMapper json){
        this.homes=homes;this.members=members;this.requests=requests;this.history=history;this.commands=commands;this.json=json;
    }
    HouseholdRequestPage list(String house,String user,List<RequestState> filter,String cursor,int limit){
        homes.requireMember(house,user);
        if(limit<1||limit>100)throw ApiException.invalid("한 번에 1~100개의 요청을 불러올 수 있어요.");
        Instant now=now();
        var states=filter==null||filter.isEmpty()?ALL:EnumSet.copyOf(filter);
        var before=cursor==null?new Cursor(now,""):Cursor.decode(cursor,now);
        var rows=requests.page(house,states,states.contains(RequestState.EXPIRED),now,cursor==null,
            before.time(),before.id(),Pageable.ofSize(limit+1));
        String next=rows.size()>limit?Cursor.encode(rows.get(limit-1)):null;
        return new HouseholdRequestPage(rows.stream().limit(limit).map(row->view(row,now)).toList(),next);
    }
    HouseholdRequestView read(String house,String user,String id){
        homes.requireMember(house,user);
        return view(scoped(house,id),now());
    }
    List<RequestHistoryItem> history(String house,String user,String id){
        homes.requireMember(house,user);
        scoped(house,id);
        return history.findByRequestIdOrderByOccurredAtAscIdAsc(id).stream().map(row->new RequestHistoryItem(
            row.actorMemberId,row.previousState,row.nextState,row.reason,row.occurredAt)).toList();
    }
    HouseholdRequestView create(String h,String user,CreateRequest input){
        Household house=homes.lock(h,user);
        Member member=homes.requireMember(h,user);
        Instant now=now();
        String title=text(input.title(),1,80,"제목");
        String body=text(input.body(),0,300,"내용");
        var targets=input.targetMemberIds()==null?List.<String>of():input.targetMemberIds().stream().distinct().sorted().toList();
        String hash=HomeService.hash(json.writeValueAsString(List.of(input.kind(),title,body,targets,
            input.dueAt()==null?"":input.dueAt().toString())));
        return once(member,"create",input.requestId(),hash,now,()->{
            validateTargets(h,targets);
            if(input.kind()==RequestKind.PROMISE&&!targets.isEmpty()&&!targets.contains(member.id))
                throw ApiException.invalid("내가 맡는 약속의 대상에는 내 이름이 포함돼야 해요.");
            var request=new HouseholdRequest();
            request.householdId=h;
            request.authorMemberId=member.id;
            request.kind=input.kind();
            request.title=title;
            request.body=body;
            request.targetsJson=json.writeValueAsString(targets);
            request.createdAt=now;
            request.updatedAt=now;
            request.dueAt=due(input.kind(),input.dueAt(),now);
            request.state=switch(input.kind()){case NOTICE->RequestState.ACTIVE;case TASK->RequestState.OPEN;case PROMISE->RequestState.CLAIMED;};
            if(input.kind()==RequestKind.PROMISE)request.assigneeMemberId=member.id;
            requests.saveAndFlush(request);
            record(request,member,null,null,now);
            homes.emit(house,"HOUSEHOLD_REQUEST_CHANGED",request.id);
            return view(request,now);
        });
    }
    HouseholdRequestView act(String h,String user,String id,String action,RequestAction input){
        Household house=homes.lock(h,user);
        Member actor=homes.requireMember(h,user);
        HouseholdRequest request=scoped(h,id);
        Instant now=now();
        if(!Set.of("claim","release","complete","cancel","reopen").contains(action))
            throw ApiException.missing();
        boolean reasonRequired=action.equals("cancel")||action.equals("reopen");
        String reason=reasonRequired?text(input.reason(),1,200,"사유"):null;
        if(!reasonRequired&&input.reason()!=null)throw ApiException.invalid("이 동작은 사유를 받지 않아요.");
        String hash=HomeService.hash(json.writeValueAsString(List.of(input.version(),Objects.toString(reason,""))));
        return once(actor,id+":"+action,input.requestId(),hash,now,()->{
            var before=request.stateAt(now);
            if(before==RequestState.EXPIRED)throw new ApiException(HttpStatus.GONE,"EXPIRED","기한이 지난 요청이에요.");
            if(action.equals("claim")&&before==RequestState.CLAIMED&&!actor.id.equals(request.assigneeMemberId))
                throw new ApiException(HttpStatus.CONFLICT,"ALREADY_CLAIMED","다른 가족이 먼저 맡았어요.");
            if(request.version!=input.version())throw ApiException.conflict("요청이 변경됐어요. 최신 내용을 확인해 주세요.");
            transition(request,actor,house.ownerId.equals(user),action,before,now);
            request.updatedAt=now;
            requests.flush();
            record(request,actor,before,reason,now);
            homes.emit(house,"HOUSEHOLD_REQUEST_CHANGED",request.id);
            return view(request,now);
        });
    }
    private void transition(HouseholdRequest request,Member actor,boolean owner,String action,RequestState before,Instant now){
        if(request.kind==RequestKind.NOTICE&&!action.equals("cancel"))
            throw ApiException.invalid("공지에는 담당자나 완료 상태를 지정할 수 없어요.");
        switch(action){
            case "claim" -> {
                requireState(before,RequestState.OPEN);
                canClaim(request,actor);
                request.assigneeMemberId=actor.id;
                request.state=RequestState.CLAIMED;
            }
            case "release" -> {
                requireState(before,RequestState.CLAIMED);
                if(!actor.id.equals(request.assigneeMemberId))throw ApiException.forbidden();
                request.assigneeMemberId=null;
                request.state=RequestState.OPEN;
            }
            case "complete" -> {
                if(before==RequestState.OPEN)canClaim(request,actor);
                else{
                    requireState(before,RequestState.CLAIMED);
                    if(!actor.id.equals(request.assigneeMemberId))throw ApiException.forbidden();
                }
                request.assigneeMemberId=actor.id;
                request.completedByMemberId=actor.id;
                request.completedAt=now;
                request.state=RequestState.DONE;
            }
            case "cancel" -> {
                requireState(before,RequestState.ACTIVE,RequestState.OPEN,RequestState.CLAIMED);
                if(!owner&&!actor.id.equals(request.authorMemberId))throw ApiException.forbidden();
                request.state=RequestState.CANCELLED;
            }
            case "reopen" -> {
                requireState(before,RequestState.DONE);
                if(!owner&&!actor.id.equals(request.completedByMemberId))throw ApiException.forbidden();
                request.assigneeMemberId=null;
                request.completedByMemberId=null;
                request.completedAt=null;
                request.state=request.dueAt.isAfter(now)?RequestState.OPEN:RequestState.EXPIRED;
            }
        }
    }
    private void canClaim(HouseholdRequest request,Member actor){
        var targets=targets(request);
        if(!targets.isEmpty()&&!targets.contains(actor.id))throw ApiException.forbidden();
    }
    private void validateTargets(String h,List<String> targets){
        if(targets.isEmpty())return;
        var active=new HashSet<String>();
        for(var member:members.findByHouseholdIdAndActiveTrueOrderById(h))active.add(member.id);
        if(!active.containsAll(targets))throw ApiException.invalid("이 집의 활성 가족만 대상이 될 수 있어요.");
    }
    private HouseholdRequest scoped(String household,String id){
        return requests.findByIdAndHouseholdId(id,household).orElseThrow(ApiException::forbidden);
    }
    private static void requireState(RequestState actual,RequestState... allowed){
        if(!List.of(allowed).contains(actual))throw ApiException.conflict("요청의 현재 상태에서 할 수 없는 동작이에요.");
    }
    private List<String> targets(HouseholdRequest request){
        return json.readValue(request.targetsJson,new TypeReference<List<String>>(){});
    }
    private HouseholdRequestView view(HouseholdRequest r,Instant now){
        return new HouseholdRequestView(r.id,r.kind,r.title,r.body,r.authorMemberId,targets(r),r.assigneeMemberId,
            r.stateAt(now),r.dueAt,r.completedByMemberId,r.completedAt,r.version,r.createdAt,r.updatedAt);
    }
    private void record(HouseholdRequest r,Member actor,RequestState before,String reason,Instant now){
        var row=new RequestTransition();
        row.requestId=r.id;row.actorMemberId=actor.id;row.previousState=before;row.nextState=r.state;
        row.reason=reason;row.occurredAt=now;history.save(row);
    }
    private HouseholdRequestView once(Member member,String scope,String key,String hash,Instant now,
        Supplier<HouseholdRequestView> write){
        var saved=commands.findByMemberIdAndCommandScopeAndRequestKey(member.id,scope,key).orElse(null);
        if(saved!=null&&saved.expiresAt.isAfter(now)){
            if(!saved.bodyHash.equals(hash))throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","동일 요청 번호의 내용이 달라요.");
            return json.readValue(saved.responseJson,HouseholdRequestView.class);
        }
        var result=write.get();
        if(saved==null){saved=new RequestCommand();saved.memberId=member.id;saved.commandScope=scope;saved.requestKey=key;}
        saved.bodyHash=hash;saved.responseJson=json.writeValueAsString(result);saved.expiresAt=now.plus(1,ChronoUnit.DAYS);
        commands.save(saved);
        return result;
    }
    @Scheduled(fixedDelay=3_600_000)
    public void purgeReceipts(){commands.purge(now());}
    static String text(String value,int min,int max,String field){
        String cleaned=value==null?"":value.strip();
        int length=cleaned.codePointCount(0,cleaned.length());
        if(length<min||length>max)throw ApiException.invalid(field+"은 "+min+"~"+max+"자로 입력해 주세요.");
        return cleaned;
    }
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    private static Instant due(RequestKind kind,Instant requested,Instant now){
        if(requested!=null){
            Instant time=requested.truncatedTo(ChronoUnit.MICROS);
            if(!time.isAfter(now)||time.isAfter(now.plus(30,ChronoUnit.DAYS)))
                throw ApiException.invalid("기한은 지금부터 30일 안으로 정해 주세요.");
            return time;
        }
        if(kind!=RequestKind.NOTICE)return now.plus(7,ChronoUnit.DAYS);
        var local=now.atZone(HOUSE_TIME);
        return local.getHour()>=23?now.plus(12,ChronoUnit.HOURS):local.toLocalDate().plusDays(1).atStartOfDay(HOUSE_TIME).toInstant();
    }
    private record Cursor(Instant time,String id){
        static Cursor decode(String encoded,Instant now){
            try{
                if(encoded.length()>160)throw new IllegalArgumentException();
                String[] parts=new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8).split(";",-1);
                if(parts.length!=2||!parts[1].matches("[a-f0-9-]{36}"))throw new IllegalArgumentException();
                Instant time=Instant.parse(parts[0]);
                if(time.isBefore(Instant.EPOCH)||time.isAfter(now))throw new IllegalArgumentException();
                return new Cursor(time,parts[1]);
            }catch(IllegalArgumentException|java.time.format.DateTimeParseException e){
                throw ApiException.invalid("요청의 다음 페이지를 확인할 수 없어요.");
            }
        }
        static String encode(HouseholdRequest row){
            return Base64.getUrlEncoder().withoutPadding().encodeToString((row.createdAt+";"+row.id).getBytes(StandardCharsets.UTF_8));
        }
    }
}

@RestController @RequestMapping("/api/households/{house}/requests")
class HouseholdRequestController {
    private final HouseholdRequestService service;
    HouseholdRequestController(HouseholdRequestService service){this.service=service;}
    @GetMapping HouseholdRequestPage list(Principal p,@PathVariable String house,
        @RequestParam(name="state",required=false) List<RequestState> states,@RequestParam(required=false) String cursor,
        @RequestParam(defaultValue="20") int limit){
        return service.list(house,p.getName(),states,cursor,limit);
    }
    @GetMapping("/{id}") HouseholdRequestView read(Principal p,@PathVariable String house,@PathVariable String id){
        return service.read(house,p.getName(),id);
    }
    @GetMapping("/{id}/history") List<RequestHistoryItem> history(Principal p,@PathVariable String house,@PathVariable String id){
        return service.history(house,p.getName(),id);
    }
    @PostMapping HouseholdRequestView create(Principal p,@PathVariable String house,@Valid @RequestBody CreateRequest input){
        return service.create(house,p.getName(),input);
    }
    @PostMapping("/{id}/{action:claim|release|complete|cancel|reopen}") HouseholdRequestView act(Principal p,
        @PathVariable String house,@PathVariable String id,@PathVariable String action,@Valid @RequestBody RequestAction input){
        return service.act(house,p.getName(),id,action,input);
    }
}
