package kr.dothome;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
class SpaceController {
    final HomeService homes;
    SpaceController(HomeService homes){this.homes=homes;}
    @GetMapping("/api/households/{id}/design") SpaceView design(@PathVariable String id,Principal p){return homes.design(id,p.getName());}
    @PatchMapping("/api/households/{id}/design") Api.World design(@PathVariable String id,Principal p,@Valid @RequestBody SpaceInput input){return homes.design(id,p.getName(),input);}
}
