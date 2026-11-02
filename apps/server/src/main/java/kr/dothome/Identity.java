package kr.dothome;

import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Service
class IdentityService {
    private final Accounts accounts;
    private final Members members;
    private final Households households;
    IdentityService(Accounts accounts,Members members,Households households) { this.accounts=accounts; this.members=members; this.households=households; }
    @Transactional
    Account resolve(String provider,String subject,String name) {
        return accounts.findByProviderAndSubject(provider,subject).orElseGet(() -> accounts.save(new Account(provider,subject,name)));
    }
    @Transactional(readOnly=true)
    Api.Me me(String userId) {
        Account account=accounts.findById(userId).orElseThrow(ApiException::forbidden);
        return new Api.Me(new Api.UserView(account.id,account.name),members.findByUserIdAndActiveTrue(userId).stream().map(m -> new Api.MembershipView(m.householdId,households.findById(m.householdId).orElseThrow().name,m.id,m.role)).toList());
    }
}
@Configuration
class SecurityConfiguration {
    static boolean localRequest(HttpServletRequest request) {
        String host=request.getServerName();
        String forwarded=request.getHeader("X-Forwarded-Host");
        return !request.isSecure() && java.util.Set.of("127.0.0.1","localhost","::1","[::1]").contains(host)
            && (forwarded==null||forwarded.matches("(localhost|127\\.0\\.0\\.1)(:[0-9]+)?"))
            && ("127.0.0.1".equals(request.getRemoteAddr())||"::1".equals(request.getRemoteAddr())||"0:0:0:0:0:0:0:1".equals(request.getRemoteAddr()));
    }
    static void validateProfiles(Environment environment) {
        if(environment.matchesProfiles("kakao"))throw new IllegalStateException("The kakao authentication profile is retired; use google");
        if(environment.matchesProfiles("dev")&&environment.matchesProfiles("prod","google"))throw new IllegalStateException("Development login cannot be combined with production/OAuth profiles");
    }
    @Bean CsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SecurityFilterChain security(HttpSecurity http,CsrfTokenRepository csrfRepository,ObjectProvider<ClientRegistrationRepository> clients,IdentityService identity,Environment environment,SocketRegistry sockets,GoogleIdentity google,@org.springframework.beans.factory.annotation.Value("${app.origin}") String appOrigin) throws Exception {
        validateProfiles(environment);
        http.authorizeHttpRequests(a -> a.requestMatchers(org.springframework.http.HttpMethod.GET,"/","/index.html","/login","/onboarding","/settings","/home/*","/invite/*","/visit/*","/assets/**","/art/**","/favicon.ico","/healthz").permitAll()
            .requestMatchers("/api/auth/csrf","/api/auth/config","/api/invitations/*","/oauth2/**","/login/**","/error").permitAll()
            .requestMatchers("/api/auth/dev").access((auth, context) -> new org.springframework.security.authorization.AuthorizationDecision(environment.matchesProfiles("dev") && localRequest(context.getRequest())))
            .anyRequest().authenticated());
        http.csrf(c -> c.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.exceptionHandling(e -> e.authenticationEntryPoint((request,response,exception) -> {
            response.setStatus(401); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요해요.\"}");
        }).accessDeniedHandler((request,response,exception) -> {
            response.setStatus(403); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한 또는 CSRF 토큰을 확인해주세요.\"}");
        }));
        http.logout(l -> l.logoutUrl("/api/logout").addLogoutHandler((request,response,authentication) -> {
            HttpSession session=request.getSession(false); if(session!=null) sockets.closeHttpSession(session.getId());
        }).logoutSuccessHandler((request,response,authentication) -> response.setStatus(204)));
        if(environment.matchesProfiles("prod"))http.headers(h -> h.httpStrictTransportSecurity(t -> t.requestMatcher(request -> true)));
        http.headers(h -> h.referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        if(clients.getIfAvailable()!=null) {
            var flow=new GoogleLoginFlow(clients.getObject(),identity,appOrigin);
            http.oauth2Login(o -> o.loginPage("/login")
                .authorizationEndpoint(a -> a.authorizationRequestResolver(flow).authorizationRequestRepository(flow))
                .userInfoEndpoint(u -> u.oidcUserService(google))
                .successHandler(flow.success()).failureHandler(flow.failure()));
        }
        return http.build();
    }
}
@RestController
class IdentityController {
    private final IdentityService identity;
    private final Environment environment;
    private final ObjectProvider<ClientRegistrationRepository> clients;
    IdentityController(IdentityService identity,Environment environment,ObjectProvider<ClientRegistrationRepository> clients) { this.identity=identity;this.environment=environment;this.clients=clients; }
    @GetMapping("/api/auth/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName(),"parameterName",token.getParameterName()); }
    @GetMapping("/api/auth/config") Map<String,Boolean> config(HttpServletRequest request) {
        return Map.of("googleEnabled",clients.getIfAvailable()!=null&&clients.getObject().findByRegistrationId("google")!=null,
            "devEnabled",environment.matchesProfiles("dev")&&SecurityConfiguration.localRequest(request));
    }
    @GetMapping("/api/me") Api.Me me(Principal principal) { return identity.me(principal.getName()); }
}
@RestController @Profile("dev & !google & !prod")
class DevelopmentLoginController {
    private final IdentityService identity;
    private final CsrfTokenRepository csrf;
    DevelopmentLoginController(IdentityService identity,CsrfTokenRepository csrf) { this.identity=identity; this.csrf=csrf; }
    @PostMapping("/api/auth/dev") Api.Me login(@Valid @RequestBody Api.DevLogin input,HttpServletRequest request,HttpServletResponse response) {
        Account account=identity.resolve("development",input.accountKey(),input.name());
        request.getSession(); request.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(account.id,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        csrf.saveToken(null,request,response);
        return identity.me(account.id);
    }
}
