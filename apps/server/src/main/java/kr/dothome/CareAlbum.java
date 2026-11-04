package kr.dothome;

import jakarta.persistence.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Entity @Table(name="care_photo")
class CarePhoto extends Row {
    @Column(nullable=false,length=36) String householdId;
    @Column(nullable=false,length=36) String guardianMemberId;
    @Column(nullable=false,length=40) String name;
    @Column(nullable=false,length=8192) String pngBase64;
    @Column(nullable=false) Instant createdAt;
}

interface CarePhotos extends JpaRepository<CarePhoto,String> {
    Slice<CarePhoto> findByHouseholdIdOrderByCreatedAtDescIdDesc(String house,Pageable page);
    Optional<CarePhoto> findByIdAndHouseholdId(String id,String house);
}

record CarePhotoView(String id,String name,String image,Instant createdAt,boolean canDelete) {}
record CareAlbumPage(List<CarePhotoView> items,boolean hasMore) {}

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class CareAlbumService {
    private static final int PAGE_SIZE=24;
    private final HomeService homes;
    private final CarePhotos photos;

    CareAlbumService(HomeService homes,CarePhotos photos){
        this.homes=homes;this.photos=photos;
    }

    @EventListener
    void snapshot(CareProfileArchived event){
        var photo=new CarePhoto();
        photo.householdId=event.household().id;
        photo.guardianMemberId=event.guardianId();
        photo.name=event.name();
        photo.createdAt=Instant.now();
        // Save the actual pixels so future art updates do not alter old portraits.
        var resource=new ClassPathResource("care-portraits/"+event.portrait()+".png");
        try(var image=resource.getInputStream()){
            photo.pngBase64=Base64.getEncoder().encodeToString(image.readAllBytes());
        }catch(IOException error){
            throw new UncheckedIOException("Care portrait could not be read",error);
        }
        photos.save(photo);
    }

    CareAlbumPage page(String house,String user,int page){
        if(page<0||page>10_000)throw ApiException.invalid("앨범 페이지를 확인해 주세요.");
        var household=homes.readLock(house,user);
        var actor=homes.requireMember(house,user);
        var rows=photos.findByHouseholdIdOrderByCreatedAtDescIdDesc(house,Pageable.ofSize(PAGE_SIZE).withPage(page));
        return new CareAlbumPage(rows.stream().map(photo->new CarePhotoView(photo.id,photo.name,
            "data:image/png;base64,"+photo.pngBase64,photo.createdAt,canDelete(household,actor,photo))).toList(),rows.hasNext());
    }

    void delete(String house,String user,String id){
        var household=homes.lock(house,user);
        var actor=homes.requireMember(house,user);
        var photo=photos.findByIdAndHouseholdId(id,house).orElseThrow(ApiException::missing);
        if(!canDelete(household,actor,photo))throw ApiException.forbidden();
        photos.delete(photo);
        homes.emit(household,"CARE_ALBUM_CHANGED",Map.of());
    }

    private static boolean canDelete(Household household,Member actor,CarePhoto photo){
        return household.pendingDeleteUntil==null &&
            (household.ownerId.equals(actor.userId)||photo.guardianMemberId.equals(actor.id));
    }
}

@RestController @RequestMapping("/api/households/{house}/care-album")
class CareAlbumController {
    private final CareAlbumService service;
    CareAlbumController(CareAlbumService service){this.service=service;}

    @GetMapping ResponseEntity<CareAlbumPage> page(@PathVariable String house,Principal user,
        @RequestParam(defaultValue="0") int page){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.page(house,user.getName(),page));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String house,@PathVariable String id,Principal user){
        service.delete(house,user.getName(),id);
    }
}
