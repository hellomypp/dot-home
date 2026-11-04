package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

enum ExportState { PENDING, READY, CANCELLED, TOO_LARGE, FAILED }

@Entity @Table(name="household_export")
class HouseholdExport extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String memberId;
    @Column(nullable=false,length=80) String requestId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) ExportState exportState=ExportState.PENDING;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant expiresAt;
    @Column(columnDefinition="LONGTEXT") String payload;
}

interface HouseholdExports extends JpaRepository<HouseholdExport,String> {
    Optional<HouseholdExport> findByMemberIdAndRequestId(String member,String requestId);
    Optional<HouseholdExport> findFirstByMemberIdOrderByCreatedAtDesc(String member);
    List<HouseholdExport> findByMemberIdAndExportStateIn(String member,Collection<ExportState> states);
    List<HouseholdExport> findByExportStateAndExpiresAtAfterOrderByCreatedAtAscIdAsc(ExportState state,Instant now,Pageable page);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from HouseholdExport e where e.expiresAt<=:now")
    @Transactional void purge(Instant now);
}

record ExportInput(@NotBlank @Size(max=80) String requestId) {}
record ExportDownload(@NotBlank @Size(max=160) String token) {}
record ExportView(String id,ExportState state,Instant expiresAt,String downloadToken) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class HouseholdExportService {
    private static final int ROW_LIMIT=10_000, BYTE_LIMIT=2_000_000;
    private static final Pageable EXPORT_PAGE=Pageable.ofSize(ROW_LIMIT+1);
    private static final String FORMER_MEMBER="퇴실한 구성원";
    private final byte[] signingKey=new SecureRandom().generateSeed(32);
    private final HomeService homes;
    private final Households households;
    private final Members members;
    private final HouseholdExports jobs;
    private final ObjectNotes notes;
    private final HouseholdRequests requests;
    private final CalendarEvents calendar;
    private final LifeEvents events;
    private final ObjectMapper json;
    @PersistenceContext private EntityManager entities;

    HouseholdExportService(HomeService homes,Households households,Members members,HouseholdExports jobs,
        ObjectNotes notes,HouseholdRequests requests,CalendarEvents calendar,LifeEvents events,ObjectMapper json){
        this.homes=homes;this.households=households;this.members=members;this.jobs=jobs;
        this.notes=notes;this.requests=requests;this.calendar=calendar;this.events=events;this.json=json;
    }

    ExportView start(String house,String user,ExportInput input){
        homes.readLock(house,user);
        Member actor=homes.requireMember(house,user);
        Instant now=Instant.now();
        var previous=jobs.findByMemberIdAndRequestId(actor.id,input.requestId()).orElse(null);
        if(previous!=null)return view(previous,now,false);
        var latest=jobs.findFirstByMemberIdOrderByCreatedAtDesc(actor.id);
        if(latest.filter(job->job.createdAt.plusSeconds(300).isAfter(now)).isPresent())
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"EXPORT_RATE_LIMIT","최근에 요청했어요. 요청한 시각에서 5분 뒤 다시 시도해 주세요.");
        var job=new HouseholdExport();
        job.householdId=house;
        job.memberId=actor.id;
        job.requestId=input.requestId();
        job.createdAt=now;
        job.expiresAt=now.plusSeconds(3600);
        jobs.save(job);
        return view(job,now,false);
    }

    List<ExportView> latest(String house,String user){
        var actor=homes.requireMember(house,user);
        var now=Instant.now();
        return jobs.findFirstByMemberIdOrderByCreatedAtDesc(actor.id).filter(job->job.expiresAt.isAfter(now))
            .map(job->List.of(view(job,now,false))).orElseGet(List::of);
    }

    ExportView status(String house,String user,String id){
        var actor=homes.requireMember(house,user);
        return view(owned(house,actor,id),Instant.now(),true);
    }

    String download(String house,String user,String id,String token){
        homes.readLock(house,user);
        var actor=homes.requireMember(house,user);
        var job=owned(house,actor,id);
        var now=Instant.now();
        if(job.exportState!=ExportState.READY||job.payload==null||!job.expiresAt.isAfter(now)||!validToken(job,token,now))
            throw new ApiException(HttpStatus.GONE,"EXPORT_DOWNLOAD_UNAVAILABLE","다운로드가 만료됐어요. 파일 상태를 다시 확인해 주세요.");
        return job.payload;
    }

    void generate(String id){
        var job=jobs.findById(id).orElse(null);
        if(job==null)return;
        var house=households.locked(job.householdId).orElse(null);
        if(house==null)return;
        entities.refresh(job);
        var now=Instant.now();
        if(job.exportState!=ExportState.PENDING||!job.expiresAt.isAfter(now))return;
        if(members.findById(job.memberId).filter(member->member.active).isEmpty()){
            job.exportState=ExportState.CANCELLED;
            return;
        }
        try{
            var document=document(house,now);
            var bytes=json.writeValueAsBytes(document);
            if(bytes.length>BYTE_LIMIT)throw new ExportLimit();
            job.payload=new String(bytes,StandardCharsets.UTF_8);
            job.exportState=ExportState.READY;
        }catch(ExportLimit limit){
            job.exportState=ExportState.TOO_LARGE;
        }
    }

    void markFailed(String id){
        var job=jobs.findById(id).orElse(null);
        if(job==null||households.locked(job.householdId).isEmpty())return;
        entities.refresh(job);
        if(job.exportState==ExportState.PENDING)job.exportState=ExportState.FAILED;
    }

    @EventListener void membershipEnding(MembershipEnding event){
        for(var job:jobs.findByMemberIdAndExportStateIn(event.memberId(),List.of(ExportState.PENDING,ExportState.READY))){
            job.exportState=ExportState.CANCELLED;
            job.payload=null;
        }
    }

    private ExportDocument document(Household house,Instant now){
        var names=new HashMap<String,String>();
        for(var member:members.findByHouseholdIdAndActiveTrueOrderById(house.id))names.put(member.id,member.name);
        var objectNotes=bounded(notes.findByHouseholdIdOrderByCreatedAtAscIdAsc(house.id,EXPORT_PAGE)).stream().map(note->
            new ExportNote(note.objectId,author(names,note.authorMemberId),note.body,note.state,note.createdAt,note.takenAt)).toList();
        var tasks=bounded(requests.findByHouseholdIdOrderByCreatedAtAscIdAsc(house.id,EXPORT_PAGE)).stream().map(request->
            new ExportRequest(request.kind,author(names,request.authorMemberId),request.title,request.body,request.stateAt(now),
                request.createdAt,request.dueAt,request.completedAt,request.careScheduledAt)).toList();
        var calendarEvents=bounded(calendar.findByHouseholdIdAndEventScopeOrderByCreatedAtAscIdAsc(house.id,"HOUSEHOLD",EXPORT_PAGE))
            .stream().map(event->new ExportCalendar(event.title,event.description,event.category,event.allDay,event.startDate,
                event.endDate,event.startAt,event.endAt,event.timeZone,author(names,event.createdByMemberId))).toList();
        var lifeEvents=bounded(events.findByHouseholdIdOrderByCreatedAtAscIdAsc(house.id,EXPORT_PAGE)).stream().map(event->
            new ExportEvent(event.type,event.message,author(names,event.authorMemberId),event.createdAt)).toList();
        return new ExportDocument(1,house.name,now,objectNotes,tasks,calendarEvents,lifeEvents);
    }

    private static <T> List<T> bounded(List<T> records){
        if(records.size()>ROW_LIMIT)throw new ExportLimit();
        return records;
    }

    private static String author(Map<String,String> names,String id){return names.getOrDefault(id,FORMER_MEMBER);}
    private static class ExportLimit extends RuntimeException {}
    private record ExportDocument(int schemaVersion,String householdName,Instant generatedAt,List<ExportNote> notes,
        List<ExportRequest> requests,List<ExportCalendar> calendar,List<ExportEvent> lifeEvents) {}
    private record ExportNote(String objectId,String author,String body,String state,Instant createdAt,Instant archivedAt) {}
    private record ExportRequest(RequestKind kind,String author,String title,String body,RequestState state,
        Instant createdAt,Instant dueAt,Instant completedAt,Instant scheduledAt) {}
    private record ExportCalendar(String title,String description,String category,boolean allDay,LocalDate startDate,
        LocalDate endDate,Instant startAt,Instant endAt,String timeZone,String author) {}
    private record ExportEvent(String type,String message,String author,Instant createdAt) {}

    private HouseholdExport owned(String house,Member actor,String id){
        return jobs.findById(id).filter(job->job.householdId.equals(house)&&job.memberId.equals(actor.id))
            .orElseThrow(ApiException::forbidden);
    }

    private ExportView view(HouseholdExport job,Instant now,boolean includeToken){
        if(!job.expiresAt.isAfter(now))throw new ApiException(HttpStatus.GONE,"EXPORT_EXPIRED","내보내기 요청이 만료됐어요.");
        long until=Math.min(job.expiresAt.getEpochSecond(),now.plusSeconds(300).getEpochSecond());
        String token=includeToken&&job.exportState==ExportState.READY?until+"."+signature(job,until):null;
        return new ExportView(job.id,job.exportState,job.expiresAt,token);
    }

    private boolean validToken(HouseholdExport job,String token,Instant now){
        if(token==null||!token.matches("[0-9]{1,12}\\.[A-Za-z0-9_-]{43}"))return false;
        int separator=token.indexOf('.');
        long until=Long.parseLong(token.substring(0,separator));
        return until>now.getEpochSecond()&&until<=job.expiresAt.getEpochSecond()
            &&MessageDigest.isEqual(signature(job,until).getBytes(StandardCharsets.US_ASCII),
                token.substring(separator+1).getBytes(StandardCharsets.US_ASCII));
    }

    private String signature(HouseholdExport job,long until){
        try{
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey,"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal((job.id+":"+job.memberId+":"+until).getBytes(StandardCharsets.US_ASCII)));
        }catch(GeneralSecurityException e){throw new IllegalStateException("Could not sign export",e);}
    }
}

@Component
class HouseholdExportWorker {
    private final HouseholdExports jobs;
    private final HouseholdExportService service;
    HouseholdExportWorker(HouseholdExports jobs,HouseholdExportService service){this.jobs=jobs;this.service=service;}

    @Scheduled(fixedDelay=3000)
    void generate(){
        for(var job:jobs.findByExportStateAndExpiresAtAfterOrderByCreatedAtAscIdAsc(
            ExportState.PENDING,Instant.now(),Pageable.ofSize(5))){
            try{service.generate(job.id);}
            catch(RuntimeException error){
                LoggerFactory.getLogger(getClass()).warn("Household export generation failed for job {}",job.id);
                service.markFailed(job.id);
            }
        }
    }

    @Scheduled(fixedDelay=300_000)
    void purge(){jobs.purge(Instant.now());}
}

@RestController @RequestMapping("/api/households/{house}/exports")
class HouseholdExportController {
    private final HouseholdExportService service;
    HouseholdExportController(HouseholdExportService service){this.service=service;}

    @GetMapping ResponseEntity<List<ExportView>> latest(@PathVariable String house,Principal user){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.latest(house,user.getName()));
    }

    @PostMapping ResponseEntity<ExportView> start(@PathVariable String house,Principal user,@Valid @RequestBody ExportInput input){
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(service.start(house,user.getName(),input));
    }

    @GetMapping("/{id}") ResponseEntity<ExportView> status(@PathVariable String house,@PathVariable String id,Principal user){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(house,user.getName(),id));
    }

    @PostMapping("/{id}/download")
    ResponseEntity<byte[]> download(@PathVariable String house,@PathVariable String id,Principal user,
        @Valid @RequestBody ExportDownload input){
        byte[] document=service.download(house,user.getName(),id,input.token()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"dot-home-records.json\"").body(document);
    }
}
