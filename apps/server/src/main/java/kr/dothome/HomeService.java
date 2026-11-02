package kr.dothome;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

record CommittedSignal(String householdId,Api.Signal signal) {}

@Service
@Transactional
class HomeService {
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    final Households households;
    final Members members;
    final Pets pets;
    final Invitations invitations;
    final PantryItems pantry;
    final PantryChanges changes;
    final LifeEvents events;
    final ApplicationEventPublisher publisher;
    HomeService(Households households,Members members,Pets pets,Invitations invitations,PantryItems pantry,PantryChanges changes,LifeEvents events,ApplicationEventPublisher publisher) {
        this.households=households;this.members=members;this.pets=pets;this.invitations=invitations;this.pantry=pantry;this.changes=changes;this.events=events;this.publisher=publisher;
    }
    Member requireMember(String householdId,String userId) {
        return members.findByHouseholdIdAndUserIdAndActiveTrue(householdId,userId).orElseThrow(ApiException::forbidden);
    }
    Household lock(String householdId,String userId) {
        requireMember(householdId,userId);
        return households.locked(householdId).orElseThrow(ApiException::missing);
    }
    void owner(Household household,String userId) { if(!household.ownerId.equals(userId)) throw ApiException.forbidden(); }
    Api.World create(String userId,Api.CreateHouse input) {
        Household household=new Household(); household.name=input.name().strip();household.ownerId=userId;household.plannedPeople=input.plannedPeople(); household.revision=1;
        households.save(household);
        Member me=member(household.id,userId,input.memberName(),input.appearance(),"OWNER"); members.save(me);
        for(Api.PetInput value:input.pets()) {
            Pet pet=new Pet();pet.householdId=household.id;pet.name=value.name().strip();pet.kind=value.kind();pet.appearance=value.appearance();pets.save(pet);
        }
        return snapshot(household,me.id);
    }
    static Member member(String householdId,String userId,String name,String appearance,String role) {
        Member member=new Member();member.householdId=householdId;member.userId=userId;member.name=name.strip();member.appearance=appearance;member.role=role; return member;
    }
    Api.World world(String householdId,String userId) { Household household=lock(householdId,userId); return snapshot(household,requireMember(householdId,userId).id); }
    Api.World plan(String householdId,String userId,Api.Plan input) {
        Household household=lock(householdId,userId);owner(household,userId);
        if(input.plannedPeople()<members.countByHouseholdIdAndActiveTrue(householdId)) throw ApiException.conflict("이미 입주한 구성원보다 계획 인원을 줄일 수 없어요.");
        household.plannedPeople=input.plannedPeople();emit(household,"HOUSEHOLD_CHANGED",Map.of());return snapshot(household,requireMember(householdId,userId).id);
    }
    Api.InvitationView invite(String householdId,String userId) {
        Household household=lock(householdId,userId);owner(household,userId);
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Invitation invitation=new Invitation();invitation.householdId=householdId;invitation.tokenHash=hash(token);invitation.expiresAt=Instant.now().plus(Duration.ofHours(72));invitations.save(invitation);
        return new Api.InvitationView(invitation.id,token,invitation.expiresAt,"/invite/"+token);
    }
    void cancel(String householdId,String userId,String invitationId) {
        Household household=lock(householdId,userId);owner(household,userId);
        Invitation invitation=invitations.locked(invitationId).orElseThrow(ApiException::missing);
        if(!invitation.householdId.equals(householdId)) throw ApiException.forbidden();
        if(invitation.acceptedBy!=null) throw ApiException.conflict("이미 수락된 초대예요.");
        invitation.cancelled=true;
    }
    @Transactional(readOnly=true)
    Api.InvitationPreview preview(String token) {
        Invitation invitation=invitation(token);available(invitation);
        Household household=households.findById(invitation.householdId).orElseThrow(ApiException::missing);
        return new Api.InvitationPreview(household.name,invitation.expiresAt,true);
    }
    // The household row lock serializes admission. READ_COMMITTED ensures queries after
    // waiting for that lock see the previous admission, not a pre-lock MySQL RR snapshot.
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    Api.World accept(String token,String userId,Api.Accept input) {
        Invitation first=invitation(token);
        Household household=households.locked(first.householdId).orElseThrow(ApiException::missing);
        Invitation invitation=invitations.locked(first.id).orElseThrow(ApiException::missing);
        entityManager.refresh(invitation,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(userId.equals(invitation.acceptedBy)) return snapshot(household,requireMember(household.id,userId).id);
        available(invitation);
        Optional<Member> existing=members.findByHouseholdIdAndUserIdAndActiveTrue(household.id,userId);
        if(existing.isPresent()) return snapshot(household,existing.get().id);
        if(members.countByHouseholdIdAndActiveTrue(household.id)>=household.plannedPeople) throw ApiException.conflict("계획한 인원이 모두 입주했어요. 대표에게 인원 변경을 요청해주세요.");
        Member member=member(household.id,userId,input.memberName(),input.appearance(),"MEMBER");members.save(member);
        invitation.acceptedBy=userId;invitation.acceptedAt=Instant.now();emit(household,"MEMBER_JOINED",memberView(member));return snapshot(household,member.id);
    }
    Invitation invitation(String token) { if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw gone(); return invitations.findByTokenHash(hash(token)).orElseThrow(HomeService::gone); }
    static void available(Invitation i) { if(i.cancelled || i.acceptedBy!=null || !i.expiresAt.isAfter(Instant.now())) throw gone(); }
    static ApiException gone() { return new ApiException(HttpStatus.GONE,"INVITATION_UNAVAILABLE","초대가 만료되었거나 사용·취소되었어요."); }
    static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    Api.World profile(String householdId,String userId,Api.Profile input) {
        Household household=lock(householdId,userId);
        String name=input.memberName()==null?"":input.memberName().strip();
        if(name.isBlank()||name.length()>40||name.codePoints().anyMatch(Character::isISOControl))throw ApiException.invalid("닉네임은 줄바꿈 없이 1~40자로 입력해 주세요.");
        Member me=requireMember(householdId,userId);
        if(!me.name.equals(name)){me.name=name;emit(household,"MEMBER_PROFILE_CHANGED",memberView(me));}
        return snapshot(household,me.id);
    }
    Api.World status(String householdId,String userId,Api.Status input) {
        Household household=lock(householdId,userId);Member me=requireMember(householdId,userId);
        if(!me.place.equals(input.place())) { me.x=320;me.y=288;me.action="idle";me.avatarUpdatedAt=Instant.now(); }
        me.place=input.place();me.mood=input.mood().strip();me.food=input.food().strip();me.thought=input.thought().strip();me.statusUpdatedAt=Instant.now();me.statusExpiresAt=me.statusUpdatedAt.plus(Duration.ofMinutes(input.expiresInMinutes()));
        emit(household,"STATUS_CHANGED",memberView(me));return snapshot(household,me.id);
    }
    List<Api.PantryView> pantry(String householdId,String userId) { requireMember(householdId,userId);return pantry.findByHouseholdIdOrderByName(householdId).stream().map(HomeService::pantryView).toList(); }
    Api.PantryView addPantry(String householdId,String userId,Api.CreatePantry input) {
        Household household=lock(householdId,userId);finiteQuantity(input.quantity());
        PantryItem item=new PantryItem();item.householdId=householdId;item.name=input.name().strip();item.location=input.location();item.quantity=input.quantity();item.unit=input.unit();item.expiresOn=input.expiresOn();pantry.save(item);
        logChange(item,requireMember(householdId,userId).id,0);emit(household,"PANTRY_CHANGED",pantryView(item));return pantryView(item);
    }
    Api.PantryView updatePantry(String householdId,String userId,String itemId,Api.UpdatePantry input) {
        Household household=lock(householdId,userId);finiteQuantity(input.quantity());PantryItem item=pantry.findById(itemId).orElseThrow(ApiException::missing);
        if(!item.householdId.equals(householdId)) throw ApiException.forbidden();
        if(item.version!=input.version()) throw ApiException.conflict("다른 구성원이 먼저 수정했어요. 최신 수량을 확인해주세요.");
        double previous=item.quantity;item.quantity=input.quantity();item.updatedAt=Instant.now();pantry.flush();logChange(item,requireMember(householdId,userId).id,previous);
        emit(household,"PANTRY_CHANGED",pantryView(item));return pantryView(item);
    }
    static void finiteQuantity(double quantity) { if(!Double.isFinite(quantity)||quantity<0||quantity>1_000_000_000) throw ApiException.invalid("수량은 0 이상의 유한한 값이어야 해요."); }
    void logChange(PantryItem item,String author,double previous) { PantryChange change=new PantryChange();change.householdId=item.householdId;change.itemId=item.id;change.authorMemberId=author;change.previousQuantity=previous;change.quantity=item.quantity;changes.save(change); }
    List<Api.EventView> events(String householdId,String userId) { requireMember(householdId,userId);return events.findTop100ByHouseholdIdOrderByCreatedAtDesc(householdId).stream().map(HomeService::eventView).toList(); }
    Api.EventView event(String householdId,String userId,Api.CreateEvent input) {
        Household household=lock(householdId,userId);Member me=requireMember(householdId,userId);
        Optional<LifeEvent> old=events.findByHouseholdIdAndAuthorMemberIdAndRequestId(householdId,me.id,input.requestId());
        if(old.isPresent()) {
            if(!old.get().message.equals(input.message().strip())||!old.get().type.equals(input.type())) throw ApiException.conflict("동일 요청 번호의 내용이 달라요.");
            return eventView(old.get());
        }
        LifeEvent event=new LifeEvent();event.householdId=householdId;event.authorMemberId=me.id;event.authorName=me.name;event.requestId=input.requestId();event.type=input.type();event.message=input.message().strip();events.save(event);emit(household,"LIFE_EVENT_CREATED",eventView(event));return eventView(event);
    }
    void emit(Household household,String type,Object payload) { household.revision++;publisher.publishEvent(new CommittedSignal(household.id,new Api.Signal(UUID.randomUUID().toString(),household.revision,type,Instant.now(),payload))); }
    Api.World snapshot(Household household,String meId) {
        return new Api.World(new Api.HouseView(household.id,household.name,household.plannedPeople,household.ownerId),meId,household.revision,Instant.now(),members.findByHouseholdIdAndActiveTrueOrderById(household.id).stream().map(HomeService::memberView).toList(),pets.findByHouseholdIdOrderById(household.id).stream().map(p->new Api.PetView(p.id,p.name,p.kind,p.appearance)).toList(),pantry.findByHouseholdIdOrderByName(household.id).stream().map(HomeService::pantryView).toList(),events.findTop100ByHouseholdIdOrderByCreatedAtDesc(household.id).stream().map(HomeService::eventView).toList());
    }
    static Api.MemberView memberView(Member m) { boolean current=m.statusExpiresAt!=null && m.statusExpiresAt.isAfter(Instant.now());return new Api.MemberView(m.id,m.userId,m.name,m.appearance,m.role,m.place,current?m.mood:"",current?m.food:"",current?m.thought:"",m.statusExpiresAt,m.statusUpdatedAt,m.x,m.y,m.action); }
    static Api.PantryView pantryView(PantryItem p) { return new Api.PantryView(p.id,p.name,p.location,p.quantity,p.unit,p.expiresOn,p.version,p.updatedAt); }
    static Api.EventView eventView(LifeEvent e) { return new Api.EventView(e.id,e.type,e.message,e.authorMemberId,e.authorName,e.createdAt); }
}
