package kr.dothome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.env.Environment;
import java.net.*;
import java.net.http.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles({"dev","test"})
class WebDeliveryIntegrationTest {
 @Autowired Environment env;
 HttpResponse<String> get(String path)throws Exception{try(var client=HttpClient.newHttpClient()){return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+env.getProperty("local.server.port")+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}}
 @Test void anonymousSpaDeepLinksUseIndexWithoutCaching()throws Exception{for(String path:new String[]{"/","/index.html","/login","/onboarding","/settings","/home/abc","/invite/"+"a".repeat(43),"/visit/"+"a".repeat(43)}){var response=get(path);assertEquals(200,response.statusCode(),path);assertTrue(response.body().contains("SPA delivery test fixture"));assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));}}
 @Test void assetsAreImmutableAndMissingAssetsStay404()throws Exception{var asset=get("/assets/test-abcd1234.js");assertEquals(200,asset.statusCode());assertTrue(asset.headers().firstValue("Cache-Control").orElse("").contains("immutable"));var missing=get("/assets/missing.js");assertEquals(404,missing.statusCode());assertFalse(missing.body().contains("SPA delivery test fixture"));}
 @Test void apiAndOauthAndWebsocketNeverFallBackToSpa()throws Exception{for(String path:new String[]{"/api/me","/api/unknown","/ws"}){var response=get(path);assertEquals(401,response.statusCode());assertFalse(response.body().contains("SPA delivery test fixture"));}var callback=get("/login/oauth2/code/unknown");assertNotEquals(200,callback.statusCode());assertFalse(callback.body().contains("SPA delivery test fixture"));}
 @Test void readinessIsPublicAndDoesNotDiscloseDatabaseDetails()throws Exception{var response=get("/healthz");assertEquals(200,response.statusCode());assertEquals("{\"status\":\"UP\"}",response.body());}
}
