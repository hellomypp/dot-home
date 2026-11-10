package kr.dothome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

record RecordArrival(@NotNull UUID requestId,@NotBlank @Size(max=300) String message) {}
record ArrivalResult(Api.EventView event,boolean alreadyHome) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class ManualArrival {
    private final HomeService homes;
    private final ExpressionStore expressions;
    private final ExpressionCommands receipts;
    private final LifeEvents events;
    private final LocationDevices devices;
    private final HouseholdNotificationService notifications;
    private final ObjectMapper json;

    ManualArrival(HomeService homes,ExpressionStore expressions,ExpressionCommands receipts,LifeEvents events,
        LocationDevices devices,HouseholdNotificationService notifications,ObjectMapper json){
        this.homes=homes;this.expressions=expressions;this.receipts=receipts;this.events=events;
        this.devices=devices;this.notifications=notifications;this.json=json;
    }

    ArrivalResult record(String h,String user,RecordArrival input){
        var house=homes.lock(h,user);
        var member=homes.requireMember(h,user);
        var now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        var requestId="arrival:"+input.requestId();
        var message=input.message().strip();
        var hash=HomeService.hash(message);
        var receipt=receipts.findByMemberIdAndRequestId(member.id,requestId).orElse(null);
        if(receipt!=null&&receipt.expiresAt.isAfter(now)){
            if(!hash.equals(receipt.bodyHash))throw changed();
            return json.readValue(receipt.responseJson,ArrivalResult.class);
        }
        // A saved event remains replayable after its short-lived state receipt is purged.
        var previous=events.findByHouseholdIdAndAuthorMemberIdAndRequestId(h,member.id,requestId).orElse(null);
        if(previous!=null){
            if(!"ARRIVED_HOME".equals(previous.type)||!message.equals(previous.message))throw changed();
            return new ArrivalResult(HomeService.eventView(previous),false);
        }

        var saved=expressions.load(member);
        var sharing=expressions.sharing(member,saved);
        var shared=expressions.publicPlace(member,saved,now);
        if(sharing.enabled()&&"UNKNOWN".equals(shared.status())){
            var device=devices.findById(user).orElse(null);
            var automatic=device!=null&&device.matches(member,sharing)?device.shared(now):null;
            if(automatic!=null)shared=automatic;
        }
        boolean alreadyHome="HOME".equals(shared.place());
        Api.EventView event=null;
        if(!alreadyHome){
            if(sharing.enabled())expressions.legacy(member,saved,"place","HOME",null,now);
            event=homes.recordEvent(house,member,requestId,"ARRIVED_HOME",message,"MANUAL",now);
            if(sharing.enabled())notifications.arrival(house,member,event.id(),now);
        }
        var result=new ArrivalResult(event,alreadyHome);
        if(receipt==null){
            receipt=new ExpressionCommand();
            receipt.memberId=member.id;
            receipt.requestId=requestId;
        }
        receipt.bodyHash=hash;
        receipt.responseJson=json.writeValueAsString(result);
        receipt.expiresAt=now.plus(1,ChronoUnit.DAYS);
        receipts.save(receipt);
        return result;
    }

    private static ApiException changed(){return ApiException.conflict("동일 요청 번호의 내용이 달라요.");}
}

@RestController
class ManualArrivalController {
    private final ManualArrival arrival;
    ManualArrivalController(ManualArrival arrival){this.arrival=arrival;}

    @PostMapping("/api/households/{id}/me/arrivals")
    ArrivalResult record(Principal user,@PathVariable String id,@Valid @RequestBody RecordArrival input){
        return arrival.record(id,user.getName(),input);
    }
}
