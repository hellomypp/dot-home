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
    final DependentProfiles dependents;
    final Invitations invitations;
    final PantryItems pantry;
    final PantryChanges changes;
    final LifeEvents events;
    final ExpressionStore expressions;
    final ApplicationEventPublisher publisher;
    HomeService(Households households,Members members,Pets pets,DependentProfiles dependents,Invitations invitations,PantryItems pantry,PantryChanges changes,LifeEvents events,ExpressionStore expressions,ApplicationEventPublisher publisher) {
        this.households=households;this.members=members;this.pets=pets;this.dependents=dependents;this.invitations=invitations;this.pantry=pantry;this.changes=changes;this.events=events;this.expressions=expressions;this.publisher=publisher;
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
        Household household=new Household(); household.name=input.name().strip();household.ownerId=userId;household.revision=1;household.presetId=input.presetId()==null?"garden_home":input.presetId();Spaces.validate(household.presetId,Map.of());
        households.save(household);
        Member me=member(household.id,userId,input.memberName(),input.appearance(),"OWNER");
        placeAtSpawn(me,household.presetId,0);
        members.save(me);
        return snapshot(household,me.id);
    }
    static Member member(String householdId,String userId,String name,String appearance,String role) {
        Member member=new Member();member.householdId=householdId;member.userId=userId;member.name=name.strip();member.appearance=appearance;member.role=role; return member;
    }
    private static void placeAtSpawn(Member member,String preset,long index) {
        var spawn=Spaces.spawn(preset,index);
        member.x=spawn[0];member.y=spawn[1];
    }
    Api.World world(String householdId,String userId) { Household household=lock(householdId,userId); return snapshot(household,requireMember(householdId,userId).id); }
    SpaceView design(String id,String user){return Spaces.view(lock(id,user));}
    Api.World design(String id,String user,SpaceInput input){
        Household h=lock(id,user);owner(h,user);Spaces.validate(input.presetId(),input.variants());
        if(h.designVersion!=input.version())throw ApiException.conflict("다른 기기에서 집 분위기를 바꿨어요. 최신 설정을 확인해 주세요.");
        h.presetId=input.presetId();try{h.objectVariants=Spaces.JSON.writeValueAsString(new TreeMap<>(input.variants()));}catch(Exception e){throw ApiException.invalid("가구 외형을 확인해 주세요.");}
        h.designVersion++;emit(h,"SPACE_DESIGN_CHANGED",Spaces.view(h));return snapshot(h,requireMember(id,user).id);
    }
    static String petName(String value) {
        if(value==null||value.strip().isBlank()||value.length()>40||value.codePoints().anyMatch(Character::isISOControl))throw ApiException.invalid("반려동물 이름은 줄바꿈 없이 1~40자로 입력해 주세요.");
        return value.strip();
    }
    Api.World addPet(String householdId,String userId,Api.PetInput input) {
        Household household=lock(householdId,userId);
        Member actor=requireMember(householdId,userId);
        PetSpecies group=PetSpecies.resolve(input.speciesGroup(),input.kind());
        if(input.appearance()!=null&&!"cream".equals(input.appearance()))
            throw ApiException.invalid("지원하지 않는 반려동물 외형이에요.");
        Pet pet=new Pet();
        pet.householdId=householdId;
        pet.name=petName(input.name());
        pet.kind=group.legacyKind();
        pet.appearance="cream";
        pet.speciesGroup=group;
        pet.speciesName=petSpeciesName(input.speciesName());
        pet.guardianMemberId=input.guardianMemberId()==null?actor.id:petGuardian(householdId,input.guardianMemberId());
        pets.save(pet);
        emit(household,"PETS_CHANGED",Map.of("petId",pet.id));
        return snapshot(household,actor.id);
    }
    Api.World editPet(String householdId,String userId,String petId,Api.PetEdit input) {
        Household household=lock(householdId,userId);
        Member actor=requireMember(householdId,userId);
        Pet pet=editablePet(household,actor,petId);
        boolean legacy=input.version()==null&&input.kind()!=null&&input.speciesGroup()==null
            &&input.speciesName()==null&&input.guardianMemberId()==null;
        if(!legacy&&(input.version()==null||input.version()!=pet.version))
            throw ApiException.conflict("다른 기기에서 반려동물 정보를 바꿨어요. 최신 정보를 확인해 주세요.");
        PetSpecies group=PetSpecies.resolve(input.speciesGroup(),input.kind());
        if(legacy&&!groupFor(pet).habitat.equals("FLOOR"))
            throw ApiException.conflict("최신 반려동물 관리 화면에서 수정해 주세요.");
        pet.name=petName(input.name());
        pet.kind=group.legacyKind();
        pet.speciesGroup=group;
        if(input.speciesName()!=null)pet.speciesName=petSpeciesName(input.speciesName());
        if(input.guardianMemberId()!=null)pet.guardianMemberId=petGuardian(householdId,input.guardianMemberId());
        pets.flush();
        emit(household,"PETS_CHANGED",Map.of("petId",pet.id));
        return snapshot(household,actor.id);
    }
    Api.World archivePet(String householdId,String userId,String petId,long version) {
        Household household=lock(householdId,userId);
        Member actor=requireMember(householdId,userId);
        Pet pet=editablePet(household,actor,petId);
        if(version!=pet.version)
            throw ApiException.conflict("다른 기기에서 반려동물 정보를 바꿨어요. 최신 정보를 확인해 주세요.");
        pet.archivedAt=Instant.now();
        pets.flush();
        emit(household,"PETS_CHANGED",Map.of("petId",pet.id));
        return snapshot(household,actor.id);
    }
    private Pet editablePet(Household household,Member actor,String petId){
        Pet pet=pets.findById(petId).orElseThrow(ApiException::missing);
        if(!pet.householdId.equals(household.id))throw ApiException.forbidden();
        if(!household.ownerId.equals(actor.userId)&&!actor.id.equals(pet.guardianMemberId))throw ApiException.forbidden();
        if(pet.archivedAt!=null)throw ApiException.missing();
        return pet;
    }
    private String petGuardian(String householdId,String memberId) {
        return members.findById(memberId).filter(m->m.active&&m.householdId.equals(householdId))
            .orElseThrow(()->ApiException.invalid("이 집의 가족을 보호자로 선택해 주세요.")).id;
    }
    private static String petSpeciesName(String value) {
        if(value==null)return "";
        if(value.length()>80||value.codePoints().anyMatch(Character::isISOControl))
            throw ApiException.invalid("종 이름은 줄바꿈 없이 80자 이하로 입력해 주세요.");
        return value.strip();
    }
    private static PetSpecies groupFor(Pet pet){
        if(pet.speciesGroup!=null)return pet.speciesGroup;
        return switch(pet.kind){case "cat" -> PetSpecies.CAT;case "dog" -> PetSpecies.DOG;default -> PetSpecies.OTHER;};
    }
    private static Api.PetView petView(Pet pet){
        var group=groupFor(pet);
        return new Api.PetView(pet.id,pet.name,pet.kind,pet.appearance,group,pet.speciesName==null?"":pet.speciesName,
            group.habitat,pet.guardianMemberId,group.appearanceId,pet.version);
    }
    Api.InvitationView invite(String householdId,String userId) {
        Household household=lock(householdId,userId);owner(household,userId);
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Invitation invitation=new Invitation();invitation.householdId=householdId;invitation.tokenHash=hash(token);invitation.expiresAt=Instant.now().plus(Duration.ofDays(7));invitations.save(invitation);
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
        Member member=member(household.id,userId,input.memberName(),input.appearance(),"MEMBER");
        placeAtSpawn(member,household.presetId,members.countByHouseholdIdAndActiveTrue(household.id));
        members.save(member);
        invitation.acceptedBy=userId;invitation.acceptedAt=Instant.now();emit(household,"MEMBER_JOINED",memberView(member,household.presetId));return snapshot(household,member.id);
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
        if(input.appearance()!=null&&!input.appearance().matches("v2:(adult_female|adult_male|child)"))throw ApiException.invalid("캐릭터 스타일을 확인해 주세요.");
        boolean changed=input.appearance()!=null&&!input.appearance().equals(me.appearance);if(changed)me.appearance=input.appearance();
        if(!me.name.equals(name)||changed){me.name=name;emit(household,"MEMBER_PROFILE_CHANGED",memberView(me,household.presetId));}
        return snapshot(household,me.id);
    }
    Api.World status(String householdId,String userId,Api.Status input) {
        Household house=lock(householdId,userId);
        Member me=requireMember(householdId,userId);
        Instant now=Instant.now();
        var saved=expressions.load(me);
        boolean changed=expressions.legacyPlace(me,saved,input.place(),input.expiresInMinutes(),now);
        if(changed)moveToSharedScene(house,me,input.place(),now);
        changed|=expressions.legacy(me,saved,"mood",input.mood(),input.expiresInMinutes(),now);
        changed|=expressions.legacy(me,saved,"food",input.food(),input.expiresInMinutes(),now);
        changed|=expressions.legacy(me,saved,"thought",input.thought(),input.expiresInMinutes(),now);
        if(changed)emit(house,"STATUS_CHANGED",memberView(me,house.presetId,saved,now,false));
        return snapshot(house,me.id);
    }
    private static void moveToSharedScene(Household house,Member member,String place,Instant now){
        String scene=switch(place.toUpperCase(Locale.ROOT)){
            case "HOME"->"home";
            case "OFFICE"->"office";
            case "SCHOOL"->"school";
            default->"outside";
        };
        if(member.place.equals(scene))return;
        var spawn=Spaces.spawn(house.presetId,0);
        member.place=scene;
        member.x=scene.equals("home")?spawn[0]:320;
        member.y=scene.equals("home")?spawn[1]:288;
        member.avatarSpace="v62";
        member.action="idle";
        member.avatarUpdatedAt=now;
    }
    ExpressionState expressions(String householdId,String userId){
        Household house=lock(householdId,userId);
        Member me=requireMember(householdId,userId);
        Instant now=Instant.now();
        return expressions.response(house,me,expressions.load(me),now);
    }
    ExpressionState expressions(String householdId,String userId,ExpressionPatch patch){
        Household house=lock(householdId,userId);
        Member me=requireMember(householdId,userId);
        return expressions.patch(house,me,patch,place->moveToSharedScene(house,me,place,Instant.now()),
            ()->emit(house,"STATUS_CHANGED",Map.of("memberId",me.id)));
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
        Instant now=Instant.now();
        var householdMembers=members.findByHouseholdIdAndActiveTrueOrderById(household.id);
        var saved=expressions.load(householdMembers);
        var memberViews=householdMembers.stream()
            .map(member->memberView(member,household.presetId,saved.getOrDefault(member.id,Map.of()),now,member.id.equals(meId))).toList();
        return new Api.World(new Api.HouseView(household.id,household.name,household.ownerId),meId,household.revision,now,memberViews,pets.findByHouseholdIdAndArchivedAtIsNullOrderById(household.id).stream().map(HomeService::petView).toList(),pantry.findByHouseholdIdOrderByName(household.id).stream().map(HomeService::pantryView).toList(),events.findTop100ByHouseholdIdOrderByCreatedAtDesc(household.id).stream().map(HomeService::eventView).toList(),Spaces.view(household),
            dependents.findByHouseholdIdAndArchivedAtIsNullOrderById(household.id).stream().map(DependentView::from).toList());
    }
    Api.MemberView memberView(Member m,String preset){
        return memberView(m,preset,expressions.load(m),Instant.now(),false);
    }
    private Api.MemberView memberView(Member m,String preset,Map<String,MemberExpression> saved,Instant now,boolean own){
        var shared=expressions.publicPlace(m,saved,now);
        // Unshared external scenes never carry their last coordinates to another member.
        boolean mask=!own&&!"MANUAL".equals(shared.status())&&!"home".equals(m.place);
        String place=mask?"outside":m.place;
        double[] point=mask?new double[]{320,288}:"home".equals(place)?Spaces.position(m,preset):new double[]{m.x,m.y};
        var current=expressions.state(m,saved,now,false);
        Instant expires=current.values().stream().map(ExpressionValue::expiresAt).max(Comparator.naturalOrder()).orElse(null);
        Instant updated=current.values().stream().map(ExpressionValue::updatedAt).max(Comparator.naturalOrder()).orElse(m.statusUpdatedAt);
        return new Api.MemberView(m.id,m.userId,m.name,m.appearance,m.role,place,
            current.containsKey("mood")?current.get("mood").value():"",
            current.containsKey("food")?current.get("food").value():"",
            current.containsKey("thought")?current.get("thought").value():"",
            expires,updated,point[0],point[1],mask?"idle":m.action,current,shared);
    }
    static Api.PantryView pantryView(PantryItem p) { return new Api.PantryView(p.id,p.name,p.location,p.quantity,p.unit,p.expiresOn,p.version,p.updatedAt); }
    static Api.EventView eventView(LifeEvent e) { return new Api.EventView(e.id,e.type,e.message,e.authorMemberId,e.authorName,e.createdAt); }
}
