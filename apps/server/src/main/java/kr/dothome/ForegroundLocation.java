package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

enum PhysicalState { HOME, OUTSIDE, UNKNOWN }

@Entity @Table(name="location_device")
class LocationDevice {
    @Id @Column(length=36) String id;
    @Column(nullable=false,length=36) String deviceId;
    long generation;
    long bindingVersion;
    @Column(length=36) String activeMemberId;
    long sharingVersion;
    @Column(length=36) String homeConfigId;
    long lastSequence;
    Instant lastObservedAt;
    @Column(length=36) String lastRequestId;
    @Enumerated(EnumType.STRING) @Column(length=12) PhysicalState physicalState;
    Instant receivedAt;
    @Enumerated(EnumType.STRING) @Column(length=12) PhysicalState lastConfirmed;
    Instant outsideAt;
    Instant lastArrivalAt;

    void bind(String memberId,long sharing,String configId){
        activeMemberId=memberId;
        sharingVersion=sharing;
        homeConfigId=configId;
        bindingVersion++;
        lastSequence=0;
        lastObservedAt=null;
        lastRequestId=null;
        physicalState=null;
        receivedAt=null;
        lastConfirmed=null;
        outsideAt=null;
    }

    boolean matches(Member member,SharingValue sharing){
        return member.id.equals(activeMemberId)&&sharing.enabled()&&sharing.version()==sharingVersion;
    }

    SharedPlace shared(Instant now){
        if(physicalState==null||physicalState==PhysicalState.UNKNOWN)return null;
        var expires=receivedAt.plus(ForegroundLocation.TTL);
        return expires.isAfter(now)?new SharedPlace("AUTO",physicalState.name(),null,receivedAt,expires):null;
    }
}

interface LocationDevices extends JpaRepository<LocationDevice,String> {
    List<LocationDevice> findByActiveMemberIdIn(Collection<String> memberIds);
}

record RegisterLocationDevice(@NotNull UUID requestId,@NotNull UUID deviceId,@NotNull Boolean replaceExisting,
    @NotNull @PositiveOrZero Long generation) {}
record LocationDeviceView(long generation,boolean currentDevice,String activeHouseId) {}
record ChangeLocationSharing(@NotNull Boolean enabled,@NotNull @Positive Long generation,
    @NotNull @PositiveOrZero Long version,UUID deviceId,UUID homeConfigId) {}
record LocationSharingView(boolean enabled,long generation,long version,boolean generalSharing,String homeConfigId) {}
record LocationObservation(@NotNull UUID requestId,@NotNull UUID deviceId,@NotNull @Positive Long generation,
    @NotNull @Positive Long version,@NotNull @Positive Long sequence,@NotNull Instant observedAt,
    @NotNull PhysicalState physicalState,@NotNull @Min(0) @Max(120) Integer fixAgeSeconds,
    @NotNull @DecimalMin("0") @DecimalMax("100000") Double accuracyMeters,@NotNull UUID homeConfigId) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class ForegroundLocation {
    static final Duration TTL=Duration.ofMinutes(5);
    private static final Duration MAX_OUTSIDE_AGE=Duration.ofHours(12);
    private static final Duration ARRIVAL_COOLDOWN=Duration.ofMinutes(10);
    private final Accounts accounts;
    private final LocationDevices devices;
    private final HomeService homes;
    private final Households households;
    private final Members members;
    private final ExpressionStore expressions;
    private final HouseholdNotificationService notifications;

    ForegroundLocation(Accounts accounts,LocationDevices devices,HomeService homes,Households households,
        Members members,ExpressionStore expressions,HouseholdNotificationService notifications){
        this.accounts=accounts;this.devices=devices;this.homes=homes;
        this.households=households;this.members=members;this.expressions=expressions;this.notifications=notifications;
    }

    LocationDeviceView device(String user,UUID browser){
        return deviceView(devices.findById(user).orElse(null),browser);
    }

    LocationDeviceView register(String user,RegisterLocationDevice input){
        lockAccount(user);
        var row=devices.findById(user).orElse(null);
        if(row!=null&&isDevice(row,input.deviceId()))return deviceView(row,input.deviceId());
        if(row!=null&&(!input.replaceExisting()||input.generation()!=row.generation))
            throw conflict("LOCATION_DEVICE_CHANGED","기존 위치 제공 기기를 확인한 뒤 교체해 주세요.");
        if(row==null){
            if(input.generation()!=0)throw conflict("LOCATION_DEVICE_CHANGED","기기 설정을 다시 불러와 주세요.");
            row=new LocationDevice();row.id=user;
        }
        var oldMember=activeMember(row);
        var oldHouse=oldMember==null?null:households.locked(oldMember.householdId).orElse(null);
        row.deviceId=input.deviceId().toString();
        row.generation++;
        row.bind(null,0,null);
        devices.save(row);
        if(oldHouse!=null)signal(oldHouse,oldMember.id);
        return deviceView(row,input.deviceId());
    }

    LocationSharingView sharing(String h,String user){
        homes.readLock(h,user);
        var member=homes.requireMember(h,user);
        return sharingView(devices.findById(user).orElse(null),member,generalSharing(member));
    }

    LocationSharingView share(String h,String user,ChangeLocationSharing input){
        homes.requireMember(h,user);
        lockAccount(user);
        var row=devices.findById(user).orElseThrow(ForegroundLocation::inactive);
        if(input.generation()!=row.generation)throw inactive();
        var oldMember=activeMember(row);
        var oldId=oldMember==null?null:oldMember.householdId;
        // When switching homes, acquire both household locks in a consistent order.
        var oldHouse=oldId!=null&&oldId.compareTo(h)<0?households.locked(oldId).orElse(null):null;
        var house=input.enabled()?homes.lock(h,user):homes.readLock(h,user);
        if(oldId!=null&&oldId.compareTo(h)>0)oldHouse=households.locked(oldId).orElse(null);
        var member=homes.requireMember(h,user);
        var general=generalSharing(member);
        var config=input.homeConfigId()==null?null:input.homeConfigId().toString();
        if(input.enabled()&&(!isDevice(row,input.deviceId())||config==null||!general.enabled()))
            throw inactive();
        boolean same=input.enabled()?row.matches(member,general)&&config.equals(row.homeConfigId)
            :!member.id.equals(row.activeMemberId);
        if(same)return sharingView(row,member,general);
        if(input.version()!=row.bindingVersion)throw conflict("LOCATION_SETTING_CHANGED","다른 화면에서 위치 설정이 바뀌었어요.");
        row.bind(input.enabled()?member.id:null,input.enabled()?general.version():0,input.enabled()?config:null);
        if(oldHouse!=null)signal(oldHouse,oldMember.id);
        signal(house,member.id);
        return sharingView(row,member,general);
    }

    void observe(String h,String user,LocationObservation input){
        homes.requireMember(h,user);
        lockAccount(user);
        var house=homes.lock(h,user);
        var member=homes.requireMember(h,user);
        var saved=expressions.load(member);
        var row=devices.findById(user).orElseThrow(ForegroundLocation::inactive);
        if(row.generation!=input.generation()||row.bindingVersion!=input.version()||!isDevice(row,input.deviceId())||
            !row.matches(member,expressions.sharing(member,saved))||!input.homeConfigId().toString().equals(row.homeConfigId))
            throw inactive();
        var now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        var observed=input.observedAt().truncatedTo(ChronoUnit.MICROS);
        if(row.lastSequence==input.sequence()&&input.requestId().toString().equals(row.lastRequestId))return;
        if(row.lastSequence>=input.sequence()||(row.lastObservedAt!=null&&!observed.isAfter(row.lastObservedAt)))
            throw conflict("LOCATION_OBSERVATION_ORDER","이미 더 최근 위치가 확인됐어요.");
        if(observed.isBefore(now.minusSeconds(120))||observed.isAfter(now.plusSeconds(30))||
            !Double.isFinite(input.accuracyMeters())||
            (input.physicalState()!=PhysicalState.UNKNOWN&&input.accuracyMeters()>100))
            throw ApiException.invalid("현재 위치를 다시 확인해 주세요.");
        row.lastSequence=input.sequence();
        row.lastObservedAt=observed;
        row.lastRequestId=input.requestId().toString();
        row.physicalState=input.physicalState();
        row.receivedAt=now;
        if(row.physicalState==PhysicalState.OUTSIDE)row.outsideAt=now;
        if(row.physicalState!=PhysicalState.UNKNOWN){
            boolean arrived=row.physicalState==PhysicalState.HOME&&row.lastConfirmed==PhysicalState.OUTSIDE
                &&row.outsideAt!=null&&row.outsideAt.isAfter(now.minus(MAX_OUTSIDE_AGE))
                &&(row.lastArrivalAt==null||!row.lastArrivalAt.isAfter(now.minus(ARRIVAL_COOLDOWN)))
                &&!"MANUAL".equals(expressions.publicPlace(member,saved,now).status());
            if(arrived){
                row.lastArrivalAt=now;
                var event=homes.recordEvent(house,member,"auto:"+UUID.randomUUID(),
                    "ARRIVED_HOME","집에 도착했어요","AUTO",now);
                notifications.arrival(house,member,event.id(),now);
            }
            row.lastConfirmed=row.physicalState;
        }
        signal(house,member.id);
    }

    private LocationDeviceView deviceView(LocationDevice row,UUID browser){
        var member=row==null?null:activeMember(row);
        String house=member!=null&&row.matches(member,generalSharing(member))?member.householdId:null;
        return new LocationDeviceView(row==null?0:row.generation,row!=null&&isDevice(row,browser),house);
    }

    private static LocationSharingView sharingView(LocationDevice row,Member member,SharingValue sharing){
        boolean active=row!=null&&row.matches(member,sharing);
        return new LocationSharingView(active,row==null?0:row.generation,
            row==null?0:row.bindingVersion,sharing.enabled(),active?row.homeConfigId:null);
    }

    private Member activeMember(LocationDevice row){
        return row.activeMemberId==null?null:members.findById(row.activeMemberId).filter(m->m.active).orElse(null);
    }

    private SharingValue generalSharing(Member member){return expressions.sharing(member,expressions.load(member));}
    private void lockAccount(String user){accounts.locked(user).orElseThrow(ApiException::forbidden);}
    private static boolean isDevice(LocationDevice row,UUID browser){return browser!=null&&browser.toString().equals(row.deviceId);}
    private void signal(Household house,String member){homes.emit(house,"STATUS_CHANGED",Map.of("memberId",member));}

    private static ApiException inactive(){
        return conflict("LOCATION_SHARING_INACTIVE","이 기기와 집의 자동 위치 공유 설정을 다시 확인해 주세요.");
    }

    private static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}

@RestController
class ForegroundLocationController {
    private final ForegroundLocation location;
    ForegroundLocationController(ForegroundLocation location){this.location=location;}

    @GetMapping("/api/me/location-device")
    LocationDeviceView device(Principal user,@RequestParam(required=false) UUID deviceId){return location.device(user.getName(),deviceId);}

    @PutMapping("/api/me/location-device")
    LocationDeviceView register(Principal user,@Valid @RequestBody RegisterLocationDevice input){
        return location.register(user.getName(),input);
    }

    @GetMapping("/api/households/{id}/me/location-sharing")
    LocationSharingView sharing(Principal user,@PathVariable String id){return location.sharing(id,user.getName());}

    @PutMapping("/api/households/{id}/me/location-sharing")
    LocationSharingView share(Principal user,@PathVariable String id,@Valid @RequestBody ChangeLocationSharing input){
        return location.share(id,user.getName(),input);
    }

    @PostMapping("/api/households/{id}/me/location-observations") @ResponseStatus(HttpStatus.NO_CONTENT)
    void observe(Principal user,@PathVariable String id,@Valid @RequestBody LocationObservation input){
        location.observe(id,user.getName(),input);
    }
}
