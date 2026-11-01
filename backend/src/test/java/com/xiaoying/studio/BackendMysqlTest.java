package com.xiaoying.studio;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties={"app.worker.enabled=false","app.storage-root=target/test-storage-mysql"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class BackendMysqlTest extends BackendContract {
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("studio_test").withUsername("tester").withPassword("test-only-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",mysql::getJdbcUrl);
        registry.add("spring.datasource.username",mysql::getUsername);
        registry.add("spring.datasource.password",mysql::getPassword);
    }
}
