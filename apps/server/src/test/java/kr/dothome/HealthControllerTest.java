package kr.dothome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.boot.availability.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
class HealthControllerTest {
 @Test void unreadyApplicationDoesNotClaimHealthy()throws Exception{var ds=mock(DataSource.class);var availability=mock(ApplicationAvailability.class);when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);assertEquals(503,new HealthController(ds,availability).health().getStatusCode().value());verifyNoInteractions(ds);}
 @Test void databaseFailureReturnsOnlyGenericStatus()throws Exception{var ds=mock(DataSource.class);var availability=mock(ApplicationAvailability.class);when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);when(ds.getConnection()).thenThrow(new SQLException("private database password"));var response=new HealthController(ds,availability).health();assertEquals(503,response.getStatusCode().value());assertEquals(Map.of("status","DOWN"),response.getBody());}
}
