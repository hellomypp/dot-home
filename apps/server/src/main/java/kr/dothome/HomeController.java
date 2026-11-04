package kr.dothome;

import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.*;

@RestController
class HomeController {
    private final HomeService service;
    HomeController(HomeService service) { this.service=service; }
    @PostMapping("/api/households") Api.World create(Principal p,@Valid @RequestBody Api.CreateHouse body) { return service.create(p.getName(),body); }
    @GetMapping("/api/households/{id}/world") Api.World world(Principal p,@PathVariable String id) { return service.world(id,p.getName()); }
    @PostMapping("/api/households/{id}/pets") Api.World pet(Principal p,@PathVariable String id,@Valid @RequestBody Api.PetInput body) { return service.addPet(id,p.getName(),body); }
    @PatchMapping("/api/households/{id}/pets/{petId}") Api.World pet(Principal p,@PathVariable String id,@PathVariable String petId,@Valid @RequestBody Api.PetEdit body) { return service.editPet(id,p.getName(),petId,body); }
    @DeleteMapping("/api/households/{id}/pets/{petId}") Api.World archivePet(Principal p,@PathVariable String id,
        @PathVariable String petId,@RequestParam @PositiveOrZero long version) {
        return service.archivePet(id,p.getName(),petId,version);
    }
    @PostMapping("/api/households/{id}/invitations") Api.InvitationView invite(Principal p,@PathVariable String id) { return service.invite(id,p.getName()); }
    @DeleteMapping("/api/households/{id}/invitations/{invitationId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void cancel(Principal p,@PathVariable String id,@PathVariable String invitationId) { service.cancel(id,p.getName(),invitationId); }
    @GetMapping("/api/invitations/{token}") Api.InvitationPreview preview(@PathVariable String token) { return service.preview(token); }
    @PostMapping("/api/invitations/{token}/accept") Api.World accept(Principal p,@PathVariable String token,@Valid @RequestBody Api.Accept body) { return service.accept(token,p.getName(),body); }
    @PatchMapping("/api/households/{id}/me/work-shift") Api.World workShift(Principal p,@PathVariable String id,
        @Valid @RequestBody Api.WorkShift body) { return service.workShift(id,p.getName(),body.working()); }
    @PatchMapping("/api/households/{id}/me/status") Api.World status(Principal p,@PathVariable String id,@Valid @RequestBody Api.Status body) { return service.status(id,p.getName(),body); }
    @PatchMapping("/api/households/{id}/me/profile") Api.World profile(Principal p,@PathVariable String id,@Valid @RequestBody Api.Profile body) { return service.profile(id,p.getName(),body); }
    @GetMapping("/api/households/{id}/pantry-items") List<Api.PantryView> pantry(Principal p,@PathVariable String id) { return service.pantry(id,p.getName()); }
    @PostMapping("/api/households/{id}/pantry-items") Api.PantryView add(Principal p,@PathVariable String id,@Valid @RequestBody Api.CreatePantry body) { return service.addPantry(id,p.getName(),body); }
    @PatchMapping("/api/households/{id}/pantry-items/{item}") Api.PantryView update(Principal p,@PathVariable String id,@PathVariable String item,@Valid @RequestBody Api.UpdatePantry body) { return service.updatePantry(id,p.getName(),item,body); }
    @GetMapping("/api/households/{id}/events") List<Api.EventView> events(Principal p,@PathVariable String id) { return service.events(id,p.getName()); }
    @PostMapping("/api/households/{id}/events") Api.EventView event(Principal p,@PathVariable String id,@Valid @RequestBody Api.CreateEvent body) { return service.event(id,p.getName(),body); }
}
