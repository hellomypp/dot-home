package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
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
    long version;

    boolean enabled(NotificationGroup group){return (allowedMask & group.bit)!=0;}
}

interface NotificationPreferences extends JpaRepository<NotificationPreference,String> {}

record NotificationSettingsView(Map<NotificationGroup,Boolean> enabled,long version) {}
record ChangeNotificationSettings(@NotEmpty Map<NotificationGroup,@NotNull Boolean> enabled,
    @NotNull @PositiveOrZero Long version) {}

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
        int mask=row.allowedMask;
        for(var entry:input.enabled().entrySet()){
            int bit=entry.getKey().bit;
            mask=entry.getValue()?mask|bit:mask&~bit;
        }
        if(mask!=row.allowedMask){
            row.allowedMask=mask;
            row.version++;
            preferences.save(row);
        }
        return view(row);
    }

    boolean allowed(String memberId,NotificationGroup group){return current(memberId).enabled(group);}

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
        return new NotificationSettingsView(enabled,row.version);
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
