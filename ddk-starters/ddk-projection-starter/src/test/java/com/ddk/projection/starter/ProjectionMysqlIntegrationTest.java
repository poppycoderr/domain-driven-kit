package com.ddk.projection.starter;

import com.ddk.test.containers.DdkContainers;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@DisplayName("读模型投影（MySQL）")
class ProjectionMysqlIntegrationTest extends ProjectionIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
