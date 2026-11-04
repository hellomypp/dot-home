package kr.dothome;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class Api {
    private Api() {}
    public record DevLogin(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{2,64}") String accountKey,@NotBlank @Size(max=40) String name) {}
    public record PetInput(@NotBlank @Size(max=40) String name,@Pattern(regexp="cat|dog") String kind,
        @Pattern(regexp="cream") String appearance,PetSpecies speciesGroup,@Size(max=80) String speciesName,
        @Size(max=36) String guardianMemberId) {
        public PetInput(String name,String kind,String appearance){this(name,kind,appearance,null,null,null);}
    }
    public record PetEdit(@NotBlank @Size(max=40) String name,@Pattern(regexp="cat|dog") String kind,
        PetSpecies speciesGroup,@Size(max=80) String speciesName,@Size(max=36) String guardianMemberId,
        @PositiveOrZero Long version) {
        public PetEdit(String name,String kind){this(name,kind,null,null,null,null);}
    }
    public record CreateHouse(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=40) String memberName,@NotBlank @Size(max=24) String appearance,@Size(max=32) String presetId) {public CreateHouse(String name,String memberName,String appearance){this(name,memberName,appearance,null);}}
    public record Profile(@NotBlank @Size(max=40) String memberName,@Size(max=24) String appearance) {public Profile(String memberName){this(memberName,null);}}
    public record Accept(@NotBlank @Size(max=40) String memberName,@NotBlank @Size(max=24) String appearance) {}
    public record Status(@Pattern(regexp="home|office|school|outside") String place,@Size(max=80) String mood,@Size(max=80) String food,@Size(max=200) String thought,@Min(1) @Max(1440) Integer expiresInMinutes) {}
    public record CreatePantry(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=40) String location,@PositiveOrZero double quantity,@NotBlank @Size(max=16) String unit,LocalDate expiresOn) {}
    public record UpdatePantry(@PositiveOrZero double quantity,@PositiveOrZero long version) {}
    public record CreateEvent(@NotBlank @Size(max=80) String requestId,@NotNull @Pattern(regexp="ARRIVED_HOME|LEFT_WORK|PARCEL_ARRIVED|NOTE") String type,@NotBlank @Size(max=300) String message) {}
    public record Intent(@NotBlank @Size(max=80) String requestId,@NotNull @Pattern(regexp="MOVE_INTENT|INTERACT_INTENT") String kind,@NotNull @Pattern(regexp="home|office|school|outside") String scene,Double x,Double y,String target) {}
    public record UserView(String id,String name) {}
    public record MembershipView(String householdId,String householdName,String memberId,String role) {}
    public record Me(UserView user,List<MembershipView> memberships) {}
    public record HouseView(String id,String name,String ownerId) {}
    public record MemberView(String id,String userId,String name,String appearance,String role,String place,String mood,String food,String thought,Instant statusExpiresAt,Instant statusUpdatedAt,double x,double y,String action,java.util.Map<String,ExpressionValue> currentState,SharedPlace sharedPlace,long membershipVersion) {}
    public record PetView(String id,String name,String kind,String appearance,PetSpecies speciesGroup,String speciesName,
        String habitat,String guardianMemberId,String appearanceId,long version) {}
    public record PantryView(String id,String name,String location,double quantity,String unit,LocalDate expiresOn,long version,Instant updatedAt) {}
    public record EventView(String id,String type,String message,String authorMemberId,String authorName,Instant createdAt) {}
    public record World(HouseView household,String meMemberId,long revision,Instant serverTime,List<MemberView> members,List<PetView> pets,List<PantryView> pantryItems,List<EventView> events,SpaceView design,List<DependentView> dependents) {}
    public record InvitationView(String id,String token,Instant expiresAt,String url) {}
    public record InvitationPreview(String householdName,Instant expiresAt,boolean available) {}
    public record Signal(String eventId,long revision,String type,Instant serverTime,Object payload) {}
}
