package kr.dothome;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.Instant;
import java.util.*;

@SpringBootTest(properties={"spring.security.oauth2.client.registration.google.client-id=test-client","spring.security.oauth2.client.registration.google.client-secret=test-secret-not-real","app.origin=http://127.0.0.1:5173"})
@ActiveProfiles({"test","google"})
class GoogleLoginIntegrationTest {
    @Autowired GoogleIdentity google;@Autowired IdentityService identity;@Autowired Accounts accounts;@Autowired HomeService homes;@Autowired ClientRegistrationRepository clients;@Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    OidcUser verified(String sub,String name,String email){Map<String,Object> claims=new HashMap<>();claims.put("sub",sub);claims.put("email",email);if(name!=null)claims.put("name",name);return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),new OidcIdToken("mock-id-token",Instant.now(),Instant.now().plusSeconds(60),claims));}
    OidcUser user(){return google.map(verified(UUID.randomUUID().toString(),"Google 구성원","same@example.test"));}
    @Test void verifiedSubjectMapsToStableInternalAccountAndSessionPrincipal()throws Exception{
        String sub=UUID.randomUUID().toString();var first=google.map(verified(sub,"첫 이름","first@example.test"));var again=google.map(verified(sub,"변경 이름","changed@example.test"));assertEquals(first.getName(),again.getName());assertNotEquals(sub,first.getName());assertEquals(sub,first.getSubject());assertEquals("google",accounts.findById(first.getName()).orElseThrow().provider);assertEquals("첫 이름",identity.me(first.getName()).user().name());
        mvc.perform(get("/api/me").with(oidcLogin().oidcUser(first))).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(first.getName()));
        var bytes=new java.io.ByteArrayOutputStream();new java.io.ObjectOutputStream(bytes).writeObject(first);var restored=(OidcUser)new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())).readObject();assertEquals(first.getName(),restored.getName());
    }
    @Test void sameEmailNeverMergesDifferentSubjectsOrProviders(){String sub=UUID.randomUUID().toString();var a=google.map(verified(sub,"A","same@example.test"));var b=google.map(verified(UUID.randomUUID().toString(),"B","same@example.test"));var dev=identity.resolve("development",sub,"개발자");assertNotEquals(a.getName(),b.getName());assertNotEquals(a.getName(),dev.id);}
    @Test void namesAreSafeInitialValuesOnly(){assertEquals("새 구성원",GoogleIdentity.displayName(null));assertEquals("새 구성원",GoogleIdentity.displayName(" \n\t "));assertEquals("가온",GoogleIdentity.displayName(" \n가온\u0000 "));assertEquals(40,GoogleIdentity.displayName("가".repeat(80)).length());assertThrows(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,()->google.map(verified(" ","A","x@example.test")));}
    @Test void googleLoginRedirectUsesFixedCallbackAndOnlyMinimalScopes()throws Exception{
        var result=mvc.perform(get("/oauth2/authorization/google").header("Host","evil.example").header("X-Forwarded-Host","evil.example").header("X-Forwarded-Proto","https")).andExpect(status().is3xxRedirection()).andReturn();String redirect=result.getResponse().getRedirectedUrl();assertTrue(redirect.startsWith("https://accounts.google.com/"));String decoded=java.net.URLDecoder.decode(redirect,java.nio.charset.StandardCharsets.UTF_8);assertTrue(decoded.contains("redirect_uri=http://127.0.0.1:5173/login/oauth2/code/google"));assertTrue(decoded.contains("response_type=code"));assertTrue(decoded.contains("state="));assertEquals(Set.of("openid","profile","email"),clients.findByRegistrationId("google").getScopes());
    }
    @Test void inviteDestinationIsBoundToOAuthStateAndNeverSentToGoogle()throws Exception{
        String next="/invite/"+"a".repeat(43);var flow=new GoogleLoginFlow(clients,identity,"http://127.0.0.1:5173");var start=new MockHttpServletRequest("GET","/oauth2/authorization/google");start.setServletPath("/oauth2/authorization/google");start.setParameter("next",next);var auth=flow.resolve(start);assertNotNull(auth);assertEquals(next,auth.getAttribute("dotHomeNext"));assertFalse(auth.getAuthorizationRequestUri().contains("invite"));flow.saveAuthorizationRequest(auth,start,new MockHttpServletResponse());
        var callback=new MockHttpServletRequest();callback.setSession((MockHttpSession)start.getSession());callback.setParameter("state",auth.getState());assertNotNull(flow.removeAuthorizationRequest(callback,new MockHttpServletResponse()));var response=new MockHttpServletResponse();var member=user();flow.success().onAuthenticationSuccess(callback,response,new OAuth2AuthenticationToken(member,member.getAuthorities(),"google"));assertEquals("http://127.0.0.1:5173"+next,response.getRedirectedUrl());assertTrue(identity.me(member.getName()).memberships().isEmpty());
        assertNull(flow.loadAuthorizationRequest(callback));
    }
    @Test void visitDestinationIsBoundToOAuthStateAndNeverSentToGoogle()throws Exception{
        String next="/visit/"+"a".repeat(43);var flow=new GoogleLoginFlow(clients,identity,"http://127.0.0.1:5173");var start=new MockHttpServletRequest("GET","/oauth2/authorization/google");start.setServletPath("/oauth2/authorization/google");start.setParameter("next",next);var auth=flow.resolve(start);assertNotNull(auth);assertEquals(next,auth.getAttribute("dotHomeNext"));assertFalse(auth.getAuthorizationRequestUri().contains("visit"));flow.saveAuthorizationRequest(auth,start,new MockHttpServletResponse());
        var callback=new MockHttpServletRequest();callback.setSession((MockHttpSession)start.getSession());callback.setParameter("state",auth.getState());assertNotNull(flow.removeAuthorizationRequest(callback,new MockHttpServletResponse()));var response=new MockHttpServletResponse();var member=user();flow.success().onAuthenticationSuccess(callback,response,new OAuth2AuthenticationToken(member,member.getAuthorities(),"google"));assertEquals("http://127.0.0.1:5173"+next,response.getRedirectedUrl());assertTrue(identity.me(member.getName()).memberships().isEmpty());
        assertNull(flow.loadAuthorizationRequest(callback));
    }
    @Test void mismatchedStateCannotRestoreDestination(){var flow=new GoogleLoginFlow(clients,identity,"http://127.0.0.1:5173");var start=new MockHttpServletRequest();var auth=OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").clientId("test").redirectUri("http://127.0.0.1:5173/login/oauth2/code/google").state("expected").attributes(a->a.put("dotHomeNext","/invite/"+"a".repeat(43))).build();flow.saveAuthorizationRequest(auth,start,new MockHttpServletResponse());var callback=new MockHttpServletRequest();callback.setSession((MockHttpSession)start.getSession());callback.setParameter("state","wrong");assertNull(flow.removeAuthorizationRequest(callback,new MockHttpServletResponse()));assertNull(callback.getAttribute("dotHomeNext"));}
    @Test void unsafeReturnTargetsAreIgnored(){for(String path:List.of("https://evil.example","//evil.example","/invite/../evil","/invite/"+"a".repeat(43)+"?next=https://evil.example","/invite/%2f%2fevil","/invite/"+"a".repeat(43)+"\r\nLocation: evil"))assertNull(GoogleLoginFlow.safeNext(path));}
    @Test void cancelAndInvalidCallbackDoNotCreateAccountsOrExposeErrors()throws Exception{
        long before=accounts.count();mvc.perform(get("/login/oauth2/code/google").param("error","access_denied").param("state","invalid")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://127.0.0.1:5173/login?error=google"));assertEquals(before,accounts.count());
        var flow=new GoogleLoginFlow(clients,identity,"http://127.0.0.1:5173");var request=new MockHttpServletRequest();request.setAttribute("dotHomeNext","/invite/"+"a".repeat(43));var response=new MockHttpServletResponse();flow.failure().onAuthenticationFailure(request,response,new org.springframework.security.oauth2.core.OAuth2AuthenticationException("sensitive-error"));assertFalse(response.getRedirectedUrl().contains("sensitive"));assertTrue(response.getRedirectedUrl().contains("next="));
    }
    @Test void firstLoginCreatesHouseOnlyOnExplicitRequestAndRevisitUsesSameCharacter()throws Exception{
        String sub=UUID.randomUUID().toString();var member=google.map(verified(sub,"가온","a@example.test"));assertTrue(identity.me(member.getName()).memberships().isEmpty());
        var result=mvc.perform(post("/api/households").with(oidcLogin().oidcUser(member)).with(csrf()).contentType("application/json").content("{\"name\":\"Google 집\",\"memberName\":\"가온\",\"appearance\":\"sage\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(1)).andReturn();
        var me=identity.me(member.getName());var revisit=google.map(verified(sub,"다른 이름","b@example.test"));assertEquals(me,identity.me(revisit.getName()));String hid=me.memberships().getFirst().householdId();
        var flow=new GoogleLoginFlow(clients,identity,"http://127.0.0.1:5173");var response=new MockHttpServletResponse();flow.success().onAuthenticationSuccess(new MockHttpServletRequest(),response,new OAuth2AuthenticationToken(revisit,revisit.getAuthorities(),"google"));assertEquals("http://127.0.0.1:5173/",response.getRedirectedUrl());
        mvc.perform(get("/api/households/"+hid+"/world").with(oidcLogin().oidcUser(user()))).andExpect(status().isForbidden());
    }
    @Test void inviteAcceptanceRequiresExplicitPostAfterGoogleLogin()throws Exception{
        var owner=user();String hid=homes.create(owner.getName(),new Api.CreateHouse("초대받을 집","대표","sage")).household().id();var invite=homes.invite(hid,owner.getName());var guest=user();
        mvc.perform(get("/api/invitations/"+invite.token())).andExpect(status().isOk());assertEquals(1,homes.world(hid,owner.getName()).members().size());assertTrue(identity.me(guest.getName()).memberships().isEmpty());
        mvc.perform(post("/api/invitations/"+invite.token()+"/accept").with(oidcLogin().oidcUser(guest)).with(csrf()).contentType("application/json").content("{\"memberName\":\"가족\",\"appearance\":\"blue\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(2));
    }
    @Test void productionDevLoginUnavailableAndUnauthenticatedApiIs401()throws Exception{
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/dev").with(csrf()).contentType("application/json").content("{\"accountKey\":\"attack\",\"name\":\"A\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/dev").with(oidcLogin().oidcUser(user())).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.googleEnabled").value(true)).andExpect(jsonPath("$.devEnabled").value(false));
    }
    @Test void logoutRemovesGoogleSession()throws Exception{var member=user();var result=mvc.perform(get("/api/me").with(oidcLogin().oidcUser(member))).andReturn();var session=(MockHttpSession)result.getRequest().getSession(false);mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());}
    @Test void newGoogleAccountReturnsOnboarding()throws Exception{var member=user();var response=new MockHttpServletResponse();new GoogleLoginFlow(clients,identity,"http://127.0.0.1:5173").success().onAuthenticationSuccess(new MockHttpServletRequest(),response,new OAuth2AuthenticationToken(member,member.getAuthorities(),"google"));assertEquals("http://127.0.0.1:5173/onboarding",response.getRedirectedUrl());}
    @Test void proxyHostAndSchemeCannotDowngradeSuccessOrFailure()throws Exception{
        String external="https://preview.example";var flow=new GoogleLoginFlow(clients,identity,external);var request=new MockHttpServletRequest();request.setServerName("127.0.0.1");request.setServerPort(8080);request.setScheme("http");request.addHeader("X-Forwarded-Host","evil.example");var member=user();var response=new MockHttpServletResponse();flow.success().onAuthenticationSuccess(request,response,new OAuth2AuthenticationToken(member,member.getAuthorities(),"google"));assertEquals(external+"/onboarding",response.getRedirectedUrl());
        response=new MockHttpServletResponse();flow.failure().onAuthenticationFailure(request,response,new org.springframework.security.oauth2.core.OAuth2AuthenticationException("cancelled"));assertEquals(external+"/login?error=google",response.getRedirectedUrl());
        request.setAttribute("dotHomeNext","/invite/"+"b".repeat(43));response=new MockHttpServletResponse();flow.success().onAuthenticationSuccess(request,response,new OAuth2AuthenticationToken(member,member.getAuthorities(),"google"));assertEquals(external+"/invite/"+"b".repeat(43),response.getRedirectedUrl());
        assertThrows(IllegalArgumentException.class,()->new GoogleLoginFlow(clients,identity,"http://public.example"));
    }
}
