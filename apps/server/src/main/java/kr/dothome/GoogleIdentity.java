package kr.dothome;

import java.io.Serializable;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.client.oidc.userinfo.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/** Delegates validation entirely to Spring; only maps the already verified OIDC user. */
@Component
class GoogleIdentity extends OidcUserService {
    private final IdentityService identity;
    GoogleIdentity(IdentityService identity) { this.identity=identity; }
    @Override public OidcUser loadUser(OidcUserRequest request) {
        if(!"google".equals(request.getClientRegistration().getRegistrationId())) throw new OAuth2AuthenticationException("unsupported_provider");
        return map(super.loadUser(request));
    }
    OidcUser map(OidcUser verified) {
        String sub=verified.getSubject();
        if(sub==null||sub.isBlank()||sub.length()>120) throw new OAuth2AuthenticationException("invalid_subject");
        Account account=identity.resolve("google",sub,displayName(verified.getFullName()));
        return new InternalOidcUser(account.id,verified);
    }
    static String displayName(String value) {
        String name=value==null?"":value.replaceAll("[\\p{Cc}\\p{Cf}]","").strip();
        if(name.isBlank()) return "새 구성원";
        return name.substring(0,name.offsetByCodePoints(0,Math.min(40,name.codePointCount(0,name.length()))));
    }
    private record InternalOidcUser(String accountId,OidcUser delegate) implements OidcUser,Serializable {
        @Override public String getName(){return accountId;}
        @Override public Map<String,Object> getClaims(){return delegate.getClaims();}
        @Override public Map<String,Object> getAttributes(){return delegate.getAttributes();}
        @Override public Collection<? extends GrantedAuthority> getAuthorities(){return delegate.getAuthorities();}
        @Override public OidcIdToken getIdToken(){return delegate.getIdToken();}
        @Override public OidcUserInfo getUserInfo(){return delegate.getUserInfo();}
    }
}

/** The destination is stored in the session's authorization request, bound to OAuth state.
 * It is an attribute, never a Google authorization query parameter. */
class GoogleLoginFlow implements OAuth2AuthorizationRequestResolver, AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final String NEXT="dotHomeNext";
    private final DefaultOAuth2AuthorizationRequestResolver resolver;
    private final HttpSessionOAuth2AuthorizationRequestRepository repository=new HttpSessionOAuth2AuthorizationRequestRepository();
    private final IdentityService identity;
    private final String origin;
    GoogleLoginFlow(ClientRegistrationRepository clients,IdentityService identity,String configuredOrigin){
        resolver=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");this.identity=identity;
        var uri=java.net.URI.create(configuredOrigin);
        boolean loopback=Set.of("127.0.0.1","localhost","[::1]").contains(Objects.toString(uri.getHost(),""));
        if(uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!(uri.getRawPath().isEmpty()||uri.getRawPath().equals("/"))||!("https".equals(uri.getScheme())||"http".equals(uri.getScheme())&&loopback))throw new IllegalArgumentException("APP_ORIGIN must be an HTTPS origin or HTTP loopback origin");
        origin=uri.getScheme()+"://"+uri.getRawAuthority();
    }
    static String safeNext(String value){return value!=null&&value.matches("/(?:invite|visit)/[A-Za-z0-9_-]{43}")?value:null;}
    private OAuth2AuthorizationRequest decorate(HttpServletRequest request,OAuth2AuthorizationRequest original){
        if(original==null)return null;
        String next=safeNext(request.getParameter("next"));
        return OAuth2AuthorizationRequest.from(original).attributes(attrs->{if(next!=null)attrs.put(NEXT,next);}).build();
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request){return decorate(request,resolver.resolve(request));}
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request,String registrationId){return decorate(request,resolver.resolve(request,registrationId));}
    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request){return repository.loadAuthorizationRequest(request);}
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest value,HttpServletRequest request,HttpServletResponse response){repository.saveAuthorizationRequest(value,request,response);}
    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,HttpServletResponse response){
        var saved=repository.removeAuthorizationRequest(request,response);
        if(saved!=null){String next=safeNext(saved.getAttribute(NEXT));if(next!=null)request.setAttribute(NEXT,next);}
        return saved;
    }
    AuthenticationSuccessHandler success(){return (request,response,auth)->{
        String next=safeNext((String)request.getAttribute(NEXT));
        if(next==null){var me=identity.me(auth.getName());next=me.memberships().isEmpty()?"/onboarding":"/home/"+me.memberships().getFirst().householdId();}
        var session=request.getSession(false);
        if(session!=null){session.removeAttribute("SPRING_SECURITY_LAST_EXCEPTION");session.removeAttribute("SPRING_SECURITY_SAVED_REQUEST");}
        response.sendRedirect(origin+next);
    };}
    AuthenticationFailureHandler failure(){return (request,response,error)->{
        var session=request.getSession(false);if(session!=null)session.removeAttribute("SPRING_SECURITY_LAST_EXCEPTION");
        String next=safeNext((String)request.getAttribute(NEXT));
        response.sendRedirect(origin+"/login?error=google"+(next==null?"":"&next="+java.net.URLEncoder.encode(next,java.nio.charset.StandardCharsets.UTF_8)));
    };}
}
