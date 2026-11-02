package kr.dothome;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @ActiveProfiles({"dev","test"})
class DevelopmentAuthenticationIntegrationTest {
 @Autowired WebApplicationContext context;MockMvc mvc;
 @Autowired HomeService homes;@Autowired IdentityService identity;
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 @Test void localFormRequiresServerPermissionAndRemoteHostCannotLogin()throws Exception{
  mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.devEnabled").value(true));
  mvc.perform(get("/api/auth/config").with(r->{r.setServerName("preview.example");return r;})).andExpect(jsonPath("$.devEnabled").value(false));
  mvc.perform(post("/api/auth/dev").with(csrf()).with(r->{r.setServerName("preview.example");return r;}).contentType("application/json").content("{\"accountKey\":\"remote-forbidden\",\"name\":\"A\"}")).andExpect(status().isUnauthorized());
 }
 @Test void nicknamePatchRequiresAuthenticationCsrfMembershipAndRejectsForgedIdentity()throws Exception{
  String owner=identity.resolve("test",java.util.UUID.randomUUID().toString(),"대표").id;
  String outsider=identity.resolve("test",java.util.UUID.randomUUID().toString(),"외부").id;
  String h=homes.create(owner,new Api.CreateHouse("닉네임 집",1,"처음","sage",java.util.List.of())).household().id();
  String path="/api/households/"+h+"/me/profile",body="{\"memberName\":\"바꾼 이름\"}";
  mvc.perform(patch(path).with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
  mvc.perform(patch(path).with(user(owner)).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(patch(path).with(user(outsider)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
  for(String invalid:new String[]{"{\"memberName\":\" \"}","{\"memberName\":\""+"x".repeat(41)+"\"}","{\"memberName\":\"위조\",\"userId\":\""+outsider+"\"}"})
   mvc.perform(patch(path).with(user(owner)).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
  mvc.perform(patch(path).with(user(owner)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.members[0].name").value("바꾼 이름"));
 }
}
