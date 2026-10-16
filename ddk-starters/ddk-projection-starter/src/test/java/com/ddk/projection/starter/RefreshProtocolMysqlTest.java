package com.ddk.projection.starter;

import com.ddk.test.containers.DdkContainers;
import org.junit.jupiter.api.DisplayName;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("投影刷新协议（MySQL）")
class RefreshProtocolMysqlTest extends RefreshProtocolTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    @Override
    protected DataSource dataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
