package kr.dothome;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@MappedSuperclass
abstract class Row {
    @Id @Column(length = 36) String id = UUID.randomUUID().toString();
}
@Entity @Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "subject"}))
class Account extends Row {
    @Column(nullable = false, length = 24) String provider;
    @Column(nullable = false, length = 120) String subject;
    @Column(nullable = false, length = 40) String name;
    protected Account() {}
    Account(String provider, String subject, String name) { this.provider=provider; this.subject=subject; this.name=name; }
}
@Entity @Table(name = "household")
class Household extends Row {
    @Column(nullable = false, length = 80) String name;
    @Column(nullable = false, length = 36) String ownerId;
    @Column(nullable=false,length=32) String presetId="classic_home";
    long designVersion;
    @Column(nullable=false,length=512) String objectVariants="{}";
    long revision;
    protected Household() {}
}
@Entity @Table(name = "membership", uniqueConstraints = @UniqueConstraint(columnNames = {"household_id", "user_id"}))
class Member extends Row {
    @Column(nullable = false, length = 36) String householdId;
    @Column(nullable = false, length = 36) String userId;
    @Column(nullable = false, length = 40) String name;
    @Column(nullable = false, length = 24) String appearance;
    @Column(nullable = false, length = 16) String role;
    @Column(nullable = false, length = 16) String place = "home";
    @Column(nullable = false, length = 80) String mood = "";
    @Column(nullable = false, length = 80) String food = "";
    @Column(nullable = false, length = 200) String thought = "";
    Instant statusExpiresAt;
    Instant statusUpdatedAt = Instant.now();
    @Column(nullable=false,length=16) String avatarSpace="v62";
    double x = 224, y = 272;
    @Column(nullable = false, length = 24) String action = "idle";
    Instant avatarUpdatedAt = Instant.now();
    boolean active = true;
    protected Member() {}
}
@Entity @Table(name = "pet")
class Pet extends Row {
    @Column(nullable = false, length = 36) String householdId;
    @Column(nullable = false, length = 40) String name;
    @Column(nullable = false, length = 24) String kind;
    @Column(nullable = false, length = 24) String appearance;
    @Enumerated(EnumType.STRING) @Column(length = 24) PetSpecies speciesGroup;
    @Column(length = 80) String speciesName;
    @Column(length = 36) String guardianMemberId;
    @Version long version;
    protected Pet() {}
}
@Entity @Table(name = "invitation")
class Invitation extends Row {
    @Column(nullable = false, length = 36) String householdId;
    @Column(nullable = false, unique = true, length = 64) String tokenHash;
    Instant expiresAt;
    boolean cancelled;
    @Column(length = 36) String acceptedBy;
    Instant acceptedAt;
    protected Invitation() {}
}
@Entity @Table(name = "pantry_item")
class PantryItem extends Row {
    @Column(nullable = false, length = 36) String householdId;
    @Column(nullable = false, length = 80) String name;
    @Column(nullable = false, length = 40) String location;
    double quantity;
    @Column(nullable = false, length = 16) String unit;
    LocalDate expiresOn;
    @Version long version;
    Instant updatedAt = Instant.now();
    protected PantryItem() {}
}
@Entity @Table(name = "pantry_change")
class PantryChange extends Row {
    @Column(nullable = false, length = 36) String householdId;
    @Column(nullable = false, length = 36) String itemId;
    @Column(nullable = false, length = 36) String authorMemberId;
    double previousQuantity, quantity;
    Instant createdAt = Instant.now();
    protected PantryChange() {}
}
@Entity @Table(name = "life_event", uniqueConstraints = @UniqueConstraint(columnNames = {"household_id", "author_member_id", "request_id"}))
class LifeEvent extends Row {
    @Column(nullable = false, length = 36) String householdId;
    @Column(nullable = false, length = 36) String authorMemberId;
    @Column(nullable = false, length = 40) String authorName;
    @Column(nullable = false, length = 80) String requestId;
    @Column(nullable = false, length = 30) String type;
    @Column(nullable = false, length = 300) String message;
    Instant createdAt = Instant.now();
    protected LifeEvent() {}
}
