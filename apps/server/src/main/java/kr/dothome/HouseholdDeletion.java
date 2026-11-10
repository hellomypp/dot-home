package kr.dothome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

record RequestHouseDeletion(@NotBlank @Size(max=80) String requestId,@NotNull @PositiveOrZero Long version) {}
record CancelHouseDeletion(@NotNull @PositiveOrZero Long version) {}
record HouseDeletionView(Instant pendingDeleteUntil,long version) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class HouseholdDeletionService {
    private final HomeService homes;
    private final Households households;
    private final Members members;
    private final HousewarmingInvitations visits;
    private final JdbcTemplate sql;
    private final ApplicationEventPublisher publisher;
    private final SpaceEditAccess spaceAccess;
    private final boolean enabled;

    HouseholdDeletionService(HomeService homes,Households households,Members members,HousewarmingInvitations visits,
        JdbcTemplate sql,ApplicationEventPublisher publisher,SpaceEditAccess spaceAccess,
        @Value("${app.household-deletion-enabled:false}") boolean enabled){
        this.homes=homes;this.households=households;this.members=members;this.visits=visits;
        this.sql=sql;this.publisher=publisher;this.spaceAccess=spaceAccess;this.enabled=enabled;
    }

    HouseDeletionView request(String id,String user,RequestHouseDeletion input){
        var house=homes.readLock(id,user);
        homes.owner(house,user);
        if(!enabled)throw ApiException.forbidden();
        if(input.requestId().equals(house.deletionRequestId))return view(house);
        checkVersion(house,input.version());
        HomeService.writable(house);
        var now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        house.pendingDeleteUntil=now.plus(Duration.ofDays(7));
        house.deletionRequestId=input.requestId();
        house.deletionVersion++;
        spaceAccess.endForHouse(id,now);
        for(var visit:visits.findByHouseholdIdOrderByCreatedAtDesc(id)){
            if(visit.revokedAt==null)visit.revokedAt=now;
        }
        homes.emit(house,"HOUSEHOLD_DELETION_CHANGED",Map.of());
        return view(house);
    }

    HouseDeletionView cancel(String id,String user,CancelHouseDeletion input){
        var house=homes.readLock(id,user);
        homes.owner(house,user);
        checkVersion(house,input.version());
        if(house.pendingDeleteUntil==null)return view(house);
        if(!house.pendingDeleteUntil.isAfter(Instant.now()))
            throw new ApiException(HttpStatus.GONE,"HOUSEHOLD_DELETION_DUE","삭제 취소 기한이 지났어요.");
        house.pendingDeleteUntil=null;
        house.deletionVersion++;
        homes.emit(house,"HOUSEHOLD_DELETION_CHANGED",Map.of());
        return view(house);
    }

    void purge(String id,Instant now){
        if(!enabled)return;
        var house=households.locked(id).orElse(null);
        if(house==null||house.pendingDeleteUntil==null||house.pendingDeleteUntil.isAfter(now))return;

        // Preserve accounts and other homes, and keep a non-content tombstone for restore.
        for(var member:members.findByHouseholdIdAndActiveTrueOrderById(id))
            publisher.publishEvent(new CommittedMembershipExit(id,member.userId));
        deleteRows(sql,id);
        sql.update("INSERT INTO household_deletion_log(household_id,deleted_at) VALUES(?,?)",statement->{
            statement.setString(1,id);
            statement.setTimestamp(2,java.sql.Timestamp.from(now),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        });
    }

    static void deleteRows(JdbcTemplate sql,String id){
        sql.update("DELETE FROM calendar_attendance WHERE event_id IN (SELECT id FROM household_calendar_event WHERE household_id=?)",id);
        sql.update("DELETE FROM request_transition WHERE request_id IN (SELECT id FROM household_request WHERE household_id=?)",id);
        sql.update("DELETE FROM notice_acknowledgement WHERE request_id IN (SELECT id FROM household_request WHERE household_id=?)",id);
        sql.update("DELETE FROM guest_access WHERE invitation_id IN (SELECT id FROM housewarming_invitation WHERE household_id=?)",id);
        sql.update("DELETE FROM member_expression WHERE member_id IN (SELECT id FROM membership WHERE household_id=?)",id);
        sql.update("DELETE FROM expression_command WHERE member_id IN (SELECT id FROM membership WHERE household_id=?)",id);
        sql.update("DELETE FROM request_command WHERE member_id IN (SELECT id FROM membership WHERE household_id=?)",id);
        for(String table:List.of("household_reaction","household_request","repeat_care_rule","object_note","household_calendar_event",
            "pantry_change","pantry_item","guestbook_entry","housewarming_invitation","invitation","life_event","dependent_profile",
            "pet","care_photo","household_export","ownership_transfer","membership")){
            sql.update("DELETE FROM "+table+" WHERE household_id=?",id);
        }
        sql.update("DELETE FROM household WHERE id=?",id);
    }

    private static void checkVersion(Household house,Long version){
        if(version==null||version!=house.deletionVersion)
            throw ApiException.conflict("집 삭제 상태가 변경됐어요. 최신 정보를 확인해 주세요.");
    }

    private static HouseDeletionView view(Household house){return new HouseDeletionView(house.pendingDeleteUntil,house.deletionVersion);}
}

@Component
class HouseholdDeletionWorker {
    private final Households households;
    private final HouseholdDeletionService service;
    private final boolean enabled;

    HouseholdDeletionWorker(Households households,HouseholdDeletionService service,@Value("${app.household-deletion-enabled:false}") boolean enabled){
        this.households=households;this.service=service;this.enabled=enabled;
    }

    @Scheduled(fixedDelay=60_000)
    void purgeDue(){
        if(!enabled)return;
        var now=Instant.now();
        for(String id:households.dueForDeletion(now,Pageable.ofSize(20))){
            try{service.purge(id,now);}
            catch(RuntimeException error){LoggerFactory.getLogger(getClass()).warn("Scheduled household purge failed for {}",id);}
        }
    }
}

@RestController @RequestMapping("/api/households/{house}/deletion")
class HouseholdDeletionController {
    private final HouseholdDeletionService service;
    HouseholdDeletionController(HouseholdDeletionService service){this.service=service;}

    @PostMapping HouseDeletionView request(@PathVariable String house,Principal user,@Valid @RequestBody RequestHouseDeletion input){
        return service.request(house,user.getName(),input);
    }

    @PostMapping("/cancel") HouseDeletionView cancel(@PathVariable String house,Principal user,@Valid @RequestBody CancelHouseDeletion input){
        return service.cancel(house,user.getName(),input);
    }
}
