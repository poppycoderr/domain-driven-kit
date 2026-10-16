package com.ddk.projection.starter;

import org.junit.jupiter.api.DisplayName;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("投影刷新协议（PostgreSQL）")
class RefreshProtocolPostgresTest extends RefreshProtocolTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Override
    protected DataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
