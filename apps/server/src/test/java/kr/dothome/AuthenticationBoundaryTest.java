package kr.dothome;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.env.MockEnvironment;
class AuthenticationBoundaryTest {
 @Test void localDevLoginCannotBeUsedFromPublicHostOrProxy(){var r=new MockHttpServletRequest();r.setRemoteAddr("127.0.0.1");r.setServerName("127.0.0.1");assertTrue(SecurityConfiguration.localRequest(r));r.addHeader("X-Forwarded-Host","preview.example");assertFalse(SecurityConfiguration.localRequest(r));r.removeHeader("X-Forwarded-Host");r.setServerName("preview.example");assertFalse(SecurityConfiguration.localRequest(r));r.setServerName("127.0.0.1");r.setSecure(true);assertFalse(SecurityConfiguration.localRequest(r));r.setSecure(false);r.setRemoteAddr("100.90.0.1");assertFalse(SecurityConfiguration.localRequest(r));}
 @Test void devAndGoogleOrProductionFailClosed(){for(String profile:new String[]{"google","prod"}){var env=new MockEnvironment();env.setActiveProfiles("dev",profile);assertThrows(IllegalStateException.class,()->SecurityConfiguration.validateProfiles(env));}var env=new MockEnvironment();env.setActiveProfiles("local","google");assertDoesNotThrow(()->SecurityConfiguration.validateProfiles(env));env.setActiveProfiles("kakao");assertThrows(IllegalStateException.class,()->SecurityConfiguration.validateProfiles(env));}
}
