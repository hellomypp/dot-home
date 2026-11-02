package kr.dothome;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
@Tag("mysql") @Testcontainers
class MigrationMySqlRetentionTest extends MigrationRetentionTest {
    @Container final MySQLContainer mysql=new MySQLContainer("mysql:8.4.7").withDatabaseName("migrationcheck").withUsername("dothome").withPassword("test-only-password");
    @Override String url(){return mysql.getJdbcUrl();}
    @Override String username(){return mysql.getUsername();}
    @Override String password(){return mysql.getPassword();}
}
