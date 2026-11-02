package kr.dothome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;
class ProductionConfigurationTest {
 static MockEnvironment valid(){
  var e=new MockEnvironment();e.setActiveProfiles("google","prod");
  String[][] settings={{"DB_URL","jdbc:mysql://mysql:3306/railway?serverTimezone=UTC"},{"DB_USER","beta"},{"DB_PASSWORD","test-only-password"},{"GOOGLE_CLIENT_ID","test.apps.googleusercontent.com"},{"GOOGLE_CLIENT_SECRET","test-only-secret"},{"APP_ORIGIN","https://beta.example.org"},{"PORT","8080"},{"spring.datasource.url","jdbc:mysql://mysql:3306/railway?serverTimezone=UTC"},{"spring.datasource.username","beta"},{"spring.datasource.password","test-only-password"},{"app.origin","https://beta.example.org"},{"spring.security.oauth2.client.registration.google.redirect-uri","https://beta.example.org/login/oauth2/code/google"},{"server.port","8080"},{"server.address","0.0.0.0"},{"spring.datasource.driver-class-name","com.mysql.cj.jdbc.Driver"},{"spring.jpa.hibernate.ddl-auto","validate"},{"spring.flyway.enabled","true"},{"server.servlet.session.cookie.secure","true"},{"server.servlet.session.cookie.http-only","true"},{"server.servlet.session.cookie.same-site","lax"}};
  for(String[] s:settings)e.setProperty(s[0],s[1]);return e;
 }
 @Test void acceptsCompleteGoogleMysqlProductionSettings(){assertDoesNotThrow(()->ProductionConfiguration.validate(valid()));}
 @Test void rejectsLocalDevTestOrMissingGoogle(){for(String bad:new String[]{"local","dev","test"}){var e=valid();e.setActiveProfiles("google","prod",bad);assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(e));}var e=valid();e.setActiveProfiles("prod");assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(e));}
 @Test void missingSettingsAndDatabaseFallbackFailWithoutLeakingValues(){for(String key:new String[]{"DB_URL","DB_USER","DB_PASSWORD","GOOGLE_CLIENT_ID","GOOGLE_CLIENT_SECRET","PORT","APP_ORIGIN"}){var e=valid();e.setProperty(key,"");assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(e));}for(String url:new String[]{"jdbc:h2:mem:prod","jdbc:mysql://user:private-secret@mysql/db","jdbc:mysql://mysql/db?password=private-secret","jdbc:mysql://mysql"}){var e=valid();e.setProperty("DB_URL",url);var failure=assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(e));assertFalse(failure.getMessage().contains("private-secret"));}}
 @Test void rejectsBadOriginAndUnsafeOverrides(){String[][] bad={{"APP_ORIGIN","http://beta.example.org"},{"APP_ORIGIN","https://beta.example.org/"},{"APP_ORIGIN","https://user:secret@beta.example.org"},{"spring.datasource.url","jdbc:h2:mem:wrong"},{"server.servlet.session.cookie.secure","false"},{"spring.jpa.hibernate.ddl-auto","update"},{"logging.level.root","DEBUG"},{"server.tomcat.accesslog.enabled","true"},{"server.port","9000"},{"spring.security.oauth2.client.registration.google.redirect-uri","https://evil.example/callback"}};for(String[] change:bad){var e=valid();e.setProperty(change[0],change[1]);assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(e),change[0]);}}
 static final AtomicBoolean started=new AtomicBoolean();
 @Configuration static class UnsafeDatabase {@Bean DataSource dataSource(){started.set(true);throw new AssertionError("Database must not start");}}
 @Test void invalidDeploymentIsRejectedBeforeDatasourceConstruction(){started.set(false);new ApplicationContextRunner().withUserConfiguration(ProductionConfiguration.class,UnsafeDatabase.class).withPropertyValues("DOT_HOME_DEPLOYMENT=beta").run(context->{assertNotNull(context.getStartupFailure());assertFalse(started.get());});}
}
