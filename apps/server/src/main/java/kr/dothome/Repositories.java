package kr.dothome;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

interface Accounts extends JpaRepository<Account,String> { Optional<Account> findByProviderAndSubject(String provider,String subject); }
interface Households extends JpaRepository<Household,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select h from Household h where h.id=:id")
    Optional<Household> locked(@Param("id") String id);
}
interface Members extends JpaRepository<Member,String> {
    Optional<Member> findByHouseholdIdAndUserIdAndActiveTrue(String householdId,String userId);
    List<Member> findByHouseholdIdAndActiveTrueOrderById(String householdId);
    List<Member> findByUserIdAndActiveTrue(String userId);
    long countByHouseholdIdAndActiveTrue(String householdId);
}
interface Pets extends JpaRepository<Pet,String> { List<Pet> findByHouseholdIdOrderById(String householdId); }
interface Invitations extends JpaRepository<Invitation,String> {
    Optional<Invitation> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invitation i where i.id=:id")
    Optional<Invitation> locked(@Param("id") String id);
}
interface PantryItems extends JpaRepository<PantryItem,String> { List<PantryItem> findByHouseholdIdOrderByName(String householdId); }
interface PantryChanges extends JpaRepository<PantryChange,String> {}
interface LifeEvents extends JpaRepository<LifeEvent,String> {
    List<LifeEvent> findTop100ByHouseholdIdOrderByCreatedAtDesc(String householdId);
    Optional<LifeEvent> findByHouseholdIdAndAuthorMemberIdAndRequestId(String householdId,String memberId,String requestId);
}
