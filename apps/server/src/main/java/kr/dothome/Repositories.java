package kr.dothome;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

interface Accounts extends JpaRepository<Account,String> { Optional<Account> findByProviderAndSubject(String provider,String subject); }
interface Households extends JpaRepository<Household,String> {
    @Query("select h.id from Household h where h.pendingDeleteUntil<=:now order by h.pendingDeleteUntil,h.id")
    List<String> dueForDeletion(java.time.Instant now,org.springframework.data.domain.Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select h from Household h where h.id=:id")
    Optional<Household> locked(@Param("id") String id);
}
interface Members extends JpaRepository<Member,String> {
    Optional<Member> findByHouseholdIdAndUserIdAndActiveTrue(String householdId,String userId);
    @Lock(LockModeType.PESSIMISTIC_READ) @Query("select m from Member m where m.householdId=:householdId and m.userId=:userId and m.active=true")
    Optional<Member> current(@Param("householdId") String householdId,@Param("userId") String userId);
    Optional<Member> findFirstByHouseholdIdAndUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(String house,String user);
    boolean existsByHouseholdIdAndUserIdAndExitRequestId(String house,String user,String key);
    List<Member> findByHouseholdIdAndActiveTrueOrderById(String householdId);
    List<Member> findByUserIdAndActiveTrue(String userId);
    long countByHouseholdIdAndActiveTrue(String householdId);
}
interface Pets extends JpaRepository<Pet,String> {
    List<Pet> findByHouseholdIdAndArchivedAtIsNullOrderById(String householdId);
    List<Pet> findByHouseholdIdAndGuardianMemberIdAndArchivedAtIsNull(String householdId,String guardian);
}
interface Invitations extends JpaRepository<Invitation,String> {
    Optional<Invitation> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invitation i where i.id=:id")
    Optional<Invitation> locked(@Param("id") String id);
}
interface PantryItems extends JpaRepository<PantryItem,String> { List<PantryItem> findByHouseholdIdOrderByName(String householdId); }
interface PantryChanges extends JpaRepository<PantryChange,String> {}
interface LifeEvents extends JpaRepository<LifeEvent,String> {
    List<LifeEvent> findByHouseholdIdOrderByCreatedAtAscIdAsc(String house,org.springframework.data.domain.Pageable page);
    List<LifeEvent> findTop100ByHouseholdIdOrderByCreatedAtDesc(String householdId);
    Optional<LifeEvent> findByHouseholdIdAndAuthorMemberIdAndRequestId(String householdId,String memberId,String requestId);
}
