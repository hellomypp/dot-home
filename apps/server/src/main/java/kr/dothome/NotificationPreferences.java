package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

enum NotificationGroup {
    EDIT(1), TASK(2), NOTICE(4), ARRIVAL(8);
    final int bit;
    NotificationGroup(int bit){this.bit=bit;}
}

@Entity @Table(name="household_notification_preference")
class NotificationPreference {
    @Id @Column(length=36) String memberId;
    @Column(nullable=false) int allowedMask=NotificationGroup.EDIT.bit|NotificationGroup.TASK.bit|NotificationGroup.NOTICE.bit;
    Integer quietStartMinute=1320;
    Integer quietEndMinute=480;
    @Column(nullable=false,length=64) String quietZone="Asia/Seoul";
    boolean lockscreenPreview;
    long version;

    boolean enabled(NotificationGroup group){return (allowedMask & group.bit)!=0;}
}

interface NotificationPreferences extends JpaRepository<NotificationPreference,String> {}

record PushQuietHours(Integer startMinute,Integer endMinute,@NotBlank @Size(max=64) String timeZone) {
    boolean includes(Instant instant){
        if(startMinute==null)return false;
        var time=instant.atZone(ZoneId.of(timeZone)).toLocalTime();
        int minute=time.getHour()*60+time.getMinute();
        return startMinute<endMinute?minute>=startMinute&&minute<endMinute:minute>=startMinute||minute<endMinute;
    }

    void validate(){
        if(!ZoneId.getAvailableZoneIds().contains(timeZone)||((startMinute==null)!=(endMinute==null))||
            (startMinute!=null&&(startMinute<0||startMinute>=1440||endMinute<0||endMinute>=1440||startMinute.equals(endMinute))))
            throw ApiException.invalid("조용한 시간의 시작·종료와 시간대를 확인해 주세요.");
    }
}
record NotificationSettingsView(Map<NotificationGroup,Boolean> enabled,long version,PushQuietHours quietHours,boolean lockscreenPreview) {}
record ChangeNotificationSettings(@Size(min=1) Map<NotificationGroup,@NotNull Boolean> enabled,
    @NotNull @PositiveOrZero Long version,@Valid PushQuietHours quietHours,Boolean lockscreenPreview) {
    ChangeNotificationSettings(Map<NotificationGroup,Boolean> enabled,Long version){this(enabled,version,null,null);}
    ChangeNotificationSettings(Map<NotificationGroup,Boolean> enabled,Long version,PushQuietHours quietHours){
        this(enabled,version,quietHours,null);
    }
}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class NotificationPreferenceService {
    private final HomeService homes;
    private final NotificationPreferences preferences;

    NotificationPreferenceService(HomeService homes,NotificationPreferences preferences){
        this.homes=homes;this.preferences=preferences;
    }

    NotificationSettingsView get(String h,String user){
        homes.readLock(h,user);
        return view(current(homes.requireMember(h,user).id));
    }

    NotificationSettingsView patch(String h,String user,ChangeNotificationSettings input){
        homes.lock(h,user);
        var row=current(homes.requireMember(h,user).id);
        if(row.version!=input.version())throw ApiException.conflict("다른 기기에서 알림 설정을 바꿨어요. 다시 불러와 주세요.");
        if(input.enabled()==null&&input.quietHours()==null&&input.lockscreenPreview()==null)throw ApiException.invalid("수정할 알림 설정을 선택해 주세요.");
        int mask=row.allowedMask;
        for(var entry:Objects.requireNonNullElse(input.enabled(),Map.<NotificationGroup,Boolean>of()).entrySet()){
            int bit=entry.getKey().bit;
            mask=entry.getValue()?mask|bit:mask&~bit;
        }
        var hours=input.quietHours();
        if(hours!=null)hours.validate();
        boolean preview=Objects.requireNonNullElse(input.lockscreenPreview(),row.lockscreenPreview);
        if(mask!=row.allowedMask||preview!=row.lockscreenPreview||hours!=null&&!hours.equals(quietHours(row))){
            row.allowedMask=mask;
            row.lockscreenPreview=preview;
            if(hours!=null){
                row.quietStartMinute=hours.startMinute();
                row.quietEndMinute=hours.endMinute();
                row.quietZone=hours.timeZone();
            }
            row.version++;
            preferences.save(row);
        }
        return view(row);
    }

    boolean allowed(String memberId,NotificationGroup group){return current(memberId).enabled(group);}

    boolean pushAllowed(String memberId,NotificationGroup group,Instant now){
        var row=current(memberId);
        return row.enabled(group)&&!quietHours(row).includes(now);
    }

    boolean lockscreenPreview(String memberId){return current(memberId).lockscreenPreview;}

    private static PushQuietHours quietHours(NotificationPreference row){
        return new PushQuietHours(row.quietStartMinute,row.quietEndMinute,row.quietZone);
    }

    private NotificationPreference current(String memberId){
        return preferences.findById(memberId).orElseGet(()->{
            var row=new NotificationPreference();
            row.memberId=memberId;
            return row;
        });
    }

    private static NotificationSettingsView view(NotificationPreference row){
        var enabled=new EnumMap<NotificationGroup,Boolean>(NotificationGroup.class);
        for(var group:NotificationGroup.values())enabled.put(group,row.enabled(group));
        return new NotificationSettingsView(enabled,row.version,quietHours(row),row.lockscreenPreview);
    }
}

@RestController @RequestMapping("/api/households/{h}/me/notification-settings")
class NotificationPreferenceController {
    private final NotificationPreferenceService service;
    NotificationPreferenceController(NotificationPreferenceService service){this.service=service;}

    @GetMapping NotificationSettingsView get(@PathVariable String h,Principal user){return service.get(h,user.getName());}
    @PatchMapping NotificationSettingsView patch(@PathVariable String h,Principal user,@Valid @RequestBody ChangeNotificationSettings input){
        return service.patch(h,user.getName(),input);
    }
}
