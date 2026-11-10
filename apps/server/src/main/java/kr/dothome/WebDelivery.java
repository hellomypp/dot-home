package kr.dothome;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.boot.availability.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

@RestController
class SpaController {
    @GetMapping(value={"/","/index.html","/login","/onboarding","/homes","/settings","/home/{id}","/invite/{token}","/visit/{token}"},produces=MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> page() {
        Resource index=new ClassPathResource("static/index.html");
        return index.exists()?ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(index):ResponseEntity.notFound().build();
    }
}
@Configuration
class AssetConfiguration implements WebMvcConfigurer {
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry){
        registry.addResourceHandler("/push-worker.js","/manifest.webmanifest")
            .addResourceLocations("classpath:/static/").setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/art/v62/**").addResourceLocations("classpath:/static/art/v62/").setCacheControl(CacheControl.maxAge(365,TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/").setCacheControl(CacheControl.maxAge(365,TimeUnit.DAYS).cachePublic().immutable());
    }
}
@RestController
class HealthController {
    final DataSource dataSource;final ApplicationAvailability availability;
    HealthController(DataSource dataSource,ApplicationAvailability availability){this.dataSource=dataSource;this.availability=availability;}
    @GetMapping("/healthz") ResponseEntity<Map<String,String>> health(){
        if(availability.getReadinessState()!=ReadinessState.ACCEPTING_TRAFFIC)return down();
        try(Connection connection=dataSource.getConnection()){if(connection.isValid(2))return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","UP"));}catch(Exception ignored){}
        return down();
    }
    static ResponseEntity<Map<String,String>> down(){return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(Map.of("status","DOWN"));}
}
