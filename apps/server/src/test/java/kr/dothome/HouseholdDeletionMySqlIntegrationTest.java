package kr.dothome;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Tag("mysql")
@Testcontainers
class HouseholdDeletionMySqlIntegrationTest extends HouseholdDeletionIntegrationTest {
    @Container
    static final MySQLContainer mysql=new MySQLContainer("mysql:8.4.7")
        .withDatabaseName("dothome").withUsername("dothome").withPassword("test-only-password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",mysql::getJdbcUrl);
        registry.add("spring.datasource.username",mysql::getUsername);
        registry.add("spring.datasource.password",mysql::getPassword);
    }
}
