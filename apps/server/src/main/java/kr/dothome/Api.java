package kr.dothome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class Api {
    private Api() {}
    public record DevLogin(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{2,64}") String accountKey,@NotBlank @Size(max=40) String name) {}
    public record PetInput(@NotBlank @Size(max=40) String name,@NotNull @Pattern(regexp="cat|dog") String kind,@NotNull @Pattern(regexp="cream") String appearance) {}
    public record PetEdit(@NotBlank @Size(max=40) String name,@NotNull @Pattern(regexp="cat|dog") String kind) {}
    public record CreateHouse(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=40) String memberName,@NotBlank @Size(max=24) String appearance) {}
    public record Profile(@NotBlank @Size(max=40) String memberName) {}
    public record Accept(@NotBlank @Size(max=40) String memberName,@NotBlank @Size(max=24) String appearance) {}
    public record Status(@NotNull @Pattern(regexp="home|office|school|outside") String place,@NotNull @Size(max=80) String mood,@NotNull @Size(max=80) String food,@NotNull @Size(max=200) String thought,@Min(1) @Max(1440) int expiresInMinutes) {}
    public record CreatePantry(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=40) String location,@PositiveOrZero double quantity,@NotBlank @Size(max=16) String unit,LocalDate expiresOn) {}
    public record UpdatePantry(@PositiveOrZero double quantity,@PositiveOrZero long version) {}
    public record CreateEvent(@NotBlank @Size(max=80) String requestId,@NotNull @Pattern(regexp="ARRIVED_HOME|LEFT_WORK|PARCEL_ARRIVED|NOTE") String type,@NotBlank @Size(max=300) String message) {}
    public record Intent(@NotBlank @Size(max=80) String requestId,@NotNull @Pattern(regexp="MOVE_INTENT|INTERACT_INTENT") String kind,@NotNull @Pattern(regexp="home|office|school|outside") String scene,Double x,Double y,String target) {}
    public record UserView(String id,String name) {}
    public record MembershipView(String householdId,String householdName,String memberId,String role) {}
    public record Me(UserView user,List<MembershipView> memberships) {}
    public record HouseView(String id,String name,String ownerId) {}
    public record MemberView(String id,String userId,String name,String appearance,String role,String place,String mood,String food,String thought,Instant statusExpiresAt,Instant statusUpdatedAt,double x,double y,String action) {}
    public record PetView(String id,String name,String kind,String appearance) {}
    public record PantryView(String id,String name,String location,double quantity,String unit,LocalDate expiresOn,long version,Instant updatedAt) {}
    public record EventView(String id,String type,String message,String authorMemberId,String authorName,Instant createdAt) {}
    public record World(HouseView household,String meMemberId,long revision,Instant serverTime,List<MemberView> members,List<PetView> pets,List<PantryView> pantryItems,List<EventView> events) {}
    public record InvitationView(String id,String token,Instant expiresAt,String url) {}
    public record InvitationPreview(String householdName,Instant expiresAt,boolean available) {}
    public record Signal(String eventId,long revision,String type,Instant serverTime,Object payload) {}
}
