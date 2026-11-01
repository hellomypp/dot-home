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
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 @Test void localFormRequiresServerPermissionAndRemoteHostCannotLogin()throws Exception{
  mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.devEnabled").value(true));
  mvc.perform(get("/api/auth/config").with(r->{r.setServerName("preview.example");return r;})).andExpect(jsonPath("$.devEnabled").value(false));
  mvc.perform(post("/api/auth/dev").with(csrf()).with(r->{r.setServerName("preview.example");return r;}).contentType("application/json").content("{\"accountKey\":\"remote-forbidden\",\"name\":\"A\"}")).andExpect(status().isUnauthorized());
 }
}
