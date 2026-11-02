package kr.dothome;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.security.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="housewarming_invitation")
class HousewarmingInvitation extends Row {
 @Column(nullable=false,length=36) String householdId,createdByMemberId;
 @Column(nullable=false,unique=true,length=64) String tokenHash;
 Instant createdAt=Instant.now(),expiresAt,revokedAt;
}
@Entity @Table(name="guest_access",uniqueConstraints=@UniqueConstraint(columnNames={"invitation_id","visitor_account_id"}))
class GuestAccess extends Row {
 @Column(nullable=false,length=36) String invitationId,visitorAccountId;
 Instant visitedAt=Instant.now();
}
@Entity @Table(name="guestbook_entry",uniqueConstraints=@UniqueConstraint(columnNames={"household_id","visitor_account_id","request_id"}))
class GuestbookEntry extends Row {
 @Column(nullable=false,length=36) String householdId,visitorAccountId;
 @Column(nullable=false,length=80) String requestId;
 @Column(nullable=false,length=1000) String message;
 Instant createdAt=Instant.now(),hiddenAt;
}
interface HousewarmingInvitations extends JpaRepository<HousewarmingInvitation,String>{Optional<HousewarmingInvitation> findByTokenHash(String hash);List<HousewarmingInvitation> findByHouseholdIdOrderByCreatedAtDesc(String h);}
interface GuestAccesses extends JpaRepository<GuestAccess,String>{List<GuestAccess> findByInvitationIdInOrderByVisitedAtDesc(List<String> ids);Optional<GuestAccess> findByInvitationIdAndVisitorAccountId(String i,String u);}
interface GuestbookEntries extends JpaRepository<GuestbookEntry,String>{List<GuestbookEntry> findTop100ByHouseholdIdOrderByCreatedAtDesc(String h);Optional<GuestbookEntry> findByHouseholdIdAndVisitorAccountIdAndRequestId(String h,String u,String r);Optional<GuestbookEntry> findFirstByHouseholdIdAndVisitorAccountIdOrderByCreatedAtDesc(String h,String u);}
record HousewarmingLink(String id,String url,Instant expiresAt) {}
record HousewarmingLinkInfo(String id,Instant expiresAt,Instant revokedAt) {}
record GuestbookInput(@NotBlank @Size(max=80) String requestId,@NotBlank @Size(max=1000) String message) {}
record GuestbookView(String id,String visitorName,String message,Instant createdAt,boolean hidden) {}
/** Deliberately contains no family memberships, private status, pantry or activity. */
record VisitorView(String name,Instant visitedAt) {}
record PublicVisit(String householdName,String theme,List<GuestbookView> guestbook,SpaceView design) {}
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class HousewarmingService {
 @PersistenceContext EntityManager entityManager;
 final Households households;final HomeService homes;final HousewarmingInvitations invites;final GuestAccesses accesses;final GuestbookEntries entries;final Accounts accounts;
 HousewarmingService(Households households,HomeService homes,HousewarmingInvitations invites,GuestAccesses accesses,GuestbookEntries entries,Accounts accounts){this.households=households;this.homes=homes;this.invites=invites;this.accesses=accesses;this.entries=entries;this.accounts=accounts;}
 HousewarmingLink create(String h,String u){Household house=homes.lock(h,u);homes.owner(house,u);byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);var i=new HousewarmingInvitation();i.householdId=h;i.createdByMemberId=homes.requireMember(h,u).id;i.tokenHash=HomeService.hash(token);i.expiresAt=Instant.now().plus(Duration.ofDays(7));invites.save(i);return new HousewarmingLink(i.id,"/visit/"+token,i.expiresAt);}
 List<HousewarmingLinkInfo> links(String h,String u){Household house=homes.lock(h,u);homes.owner(house,u);return invites.findByHouseholdIdOrderByCreatedAtDesc(h).stream().map(i->new HousewarmingLinkInfo(i.id,i.expiresAt,i.revokedAt)).toList();}
 void revoke(String h,String u,String id){Household house=homes.lock(h,u);homes.owner(house,u);var i=invites.findById(id).orElseThrow(ApiException::missing);if(!i.householdId.equals(h))throw ApiException.forbidden();i.revokedAt=Instant.now();}
 HousewarmingInvitation invitation(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw HomeService.gone();var i=invites.findByTokenHash(HomeService.hash(token)).orElseThrow(HomeService::gone);available(i);return i;}
 void available(HousewarmingInvitation i){if(i.revokedAt!=null||i.expiresAt==null||!i.expiresAt.isAfter(Instant.now()))throw HomeService.gone();}
 PublicVisit enter(String token,String u){var first=invitation(token);households.locked(first.householdId).orElseThrow(ApiException::missing);entityManager.refresh(first);var i=first;available(i);accounts.findById(u).orElseThrow(ApiException::forbidden);if(accesses.findByInvitationIdAndVisitorAccountId(i.id,u).isEmpty()){var a=new GuestAccess();a.invitationId=i.id;a.visitorAccountId=u;accesses.save(a);}return publicView(i);}
 HousewarmingInvitation access(String token,String u){var i=invitation(token);if(accesses.findByInvitationIdAndVisitorAccountId(i.id,u).isEmpty())throw ApiException.forbidden();return i;}
 PublicVisit world(String token,String u){return publicView(access(token,u));}
 PublicVisit publicView(HousewarmingInvitation i){Household house=households.findById(i.householdId).orElseThrow(ApiException::missing);return new PublicVisit(house.name,"warm",entries.findTop100ByHouseholdIdOrderByCreatedAtDesc(house.id).stream().filter(e->e.hiddenAt==null).map(this::view).toList(),Spaces.view(house));}
 GuestbookView write(String token,String u,GuestbookInput input){var first=access(token,u);households.locked(first.householdId).orElseThrow(ApiException::missing);entityManager.refresh(first);available(first);var i=first;String message=input.message().strip();if(message.isEmpty()||message.length()>1000)throw ApiException.invalid("방명록은 1~1000자로 입력해주세요.");var previous=entries.findByHouseholdIdAndVisitorAccountIdAndRequestId(i.householdId,u,input.requestId());if(previous.isPresent()){if(!previous.get().message.equals(message))throw ApiException.conflict("동일 요청의 내용이 달라요.");return view(previous.get());}var last=entries.findFirstByHouseholdIdAndVisitorAccountIdOrderByCreatedAtDesc(i.householdId,u);if(last.isPresent()&&last.get().createdAt.isAfter(Instant.now().minusSeconds(30)))throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","30초 후 다시 남겨주세요.");var e=new GuestbookEntry();e.householdId=i.householdId;e.visitorAccountId=u;e.requestId=input.requestId();e.message=message;entries.saveAndFlush(e);return view(e);}
 List<VisitorView> visitors(String h,String u){Household house=homes.lock(h,u);homes.owner(house,u);return accesses.findByInvitationIdInOrderByVisitedAtDesc(invites.findByHouseholdIdOrderByCreatedAtDesc(h).stream().map(i->i.id).toList()).stream().map(a->new VisitorView(accounts.findById(a.visitorAccountId).map(v->v.name).orElse("방문자"),a.visitedAt)).toList();}
 List<GuestbookView> manage(String h,String u){Household house=homes.lock(h,u);homes.owner(house,u);return entries.findTop100ByHouseholdIdOrderByCreatedAtDesc(h).stream().map(this::view).toList();}
 void hide(String h,String u,String id){Household house=homes.lock(h,u);homes.owner(house,u);var e=entries.findById(id).orElseThrow(ApiException::missing);if(!e.householdId.equals(h))throw ApiException.forbidden();e.hiddenAt=Instant.now();}
 GuestbookView view(GuestbookEntry e){return new GuestbookView(e.id,accounts.findById(e.visitorAccountId).map(a->a.name).orElse("방문자"),e.message,e.createdAt,e.hiddenAt!=null);}
}
@RestController
class HousewarmingController {
 final HousewarmingService service;HousewarmingController(HousewarmingService s){service=s;}
 @PostMapping("/api/households/{h}/housewarming-invitations") HousewarmingLink create(@PathVariable String h,Principal p){return service.create(h,p.getName());}
 @GetMapping("/api/households/{h}/housewarming-invitations") List<HousewarmingLinkInfo> links(@PathVariable String h,Principal p){return service.links(h,p.getName());}
 @DeleteMapping("/api/households/{h}/housewarming-invitations/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void revoke(@PathVariable String h,@PathVariable String id,Principal p){service.revoke(h,p.getName(),id);}
 @PostMapping("/api/visits/{token}/accept") PublicVisit enter(@PathVariable String token,Principal p){return service.enter(token,p.getName());}
 @GetMapping("/api/visits/{token}") PublicVisit world(@PathVariable String token,Principal p){return service.world(token,p.getName());}
 @PostMapping("/api/visits/{token}/guestbook") GuestbookView write(@PathVariable String token,Principal p,@Valid @RequestBody GuestbookInput input){return service.write(token,p.getName(),input);}
 @GetMapping("/api/households/{h}/visitors") List<VisitorView> visitors(@PathVariable String h,Principal p){return service.visitors(h,p.getName());}
 @GetMapping("/api/households/{h}/guestbook") List<GuestbookView> manage(@PathVariable String h,Principal p){return service.manage(h,p.getName());}
 @DeleteMapping("/api/households/{h}/guestbook/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void hide(@PathVariable String h,@PathVariable String id,Principal p){service.hide(h,p.getName(),id);}
}
