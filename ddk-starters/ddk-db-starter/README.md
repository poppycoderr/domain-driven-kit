# DDK DB Starter

This starter simplifies the configuration and management of multiple data sources within a DDK application. It allows you to define several database connections and registers corresponding `DataSource`, `PlatformTransactionManager`, and `JdbcTemplate` beans for each.

## Features

-   **Multiple Data Source Configuration:** Define connection properties for several databases in your `application.properties` or `application.yml`.
-   **Dynamic Bean Registration:** Automatically creates:
    -   A named `DataSource` bean for each configured source.
    -   A named `DataSourceTransactionManager` for each source.
    -   A named `JdbcTemplate` for each source.
-   **Primary DataSource:** Designate one data source as `@Primary`, making its beans the default autowire candidates.
-   **Flexible Properties:** Configure URL, username, password, driver class, and DataSource type (e.g., HikariCP) for each source.

## Dependencies

-   `spring-boot-starter-jdbc`

## Configuration

Define your data sources under the `ddk.datasource` prefix in your `application.properties` or `application.yml` file.

**Properties Structure:**

-   `ddk.datasource.primary`: (String, Mandatory) The `name` of the data source that should be considered primary. The auto-configuration will not activate if this is not set.
-   `ddk.datasource.sources`: (List of `DataSourceProperty`) A list where each element defines a data source.

**`DataSourceProperty` attributes:**

-   `name`: (String, Mandatory) A unique name for this data source (e.g., "mainDb", "auditDb"). This name is used to create bean qualifiers (e.g., "mainDbDataSource", "mainDbJdbcTemplate").
-   `url`: (String, Mandatory) The JDBC URL of the database.
-   `username`: (String) The login username of the database.
-   `password`: (String) The login password of the database.
-   `driverClassName`: (String) Fully qualified name of the JDBC driver. Spring Boot can often infer this from the URL.
-   `type`: (Class<? extends javax.sql.DataSource>) Optional. The specific `javax.sql.DataSource` implementation type to use (e.g., `com.zaxxer.hikari.HikariDataSource`). If not specified, Spring Boot's default logic applies (usually HikariCP if available).

**Example `application.properties`:**

```properties
# Designate 'mainDb' as the primary data source
ddk.datasource.primary=mainDb

# Configuration for the 'mainDb' data source
ddk.datasource.sources[0].name=mainDb
ddk.datasource.sources[0].url=jdbc:h2:mem:mainDb;DB_CLOSE_DELAY=-1
ddk.datasource.sources[0].username=sa
ddk.datasource.sources[0].password=
ddk.datasource.sources[0].driverClassName=org.h2.Driver
ddk.datasource.sources[0].type=com.zaxxer.hikari.HikariDataSource

# Configuration for the 'auditDb' data source
ddk.datasource.sources[1].name=auditDb
ddk.datasource.sources[1].url=jdbc:h2:mem:auditDb;DB_CLOSE_DELAY=-1
ddk.datasource.sources[1].username=sa
ddk.datasource.sources[1].password=
ddk.datasource.sources[1].driverClassName=org.h2.Driver
```

## How to Use

1.  **Include the starter in your project's `pom.xml`:**

    ```xml
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-db-starter</artifactId>
        <version>${ddk.version}</version> <!-- Ensure this matches your project's ddk version -->
    </dependency>
    ```

2.  **Configure your data sources** as shown in the example above.

3.  **Inject and use the beans:**

    You can inject the `DataSource`, `JdbcTemplate`, or `PlatformTransactionManager` for a specific data source using the `@Qualifier` annotation. The bean names are constructed as `sourceName + "DataSource"`, `sourceName + "JdbcTemplate"`, and `sourceName + "TransactionManager"`.

    ```java
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.beans.factory.annotation.Qualifier;
    import org.springframework.jdbc.core.JdbcTemplate;
    import org.springframework.stereotype.Service;
    import javax.sql.DataSource;

    @Service
    public class MyDataService {

        private final JdbcTemplate mainDbJdbcTemplate;
        private final JdbcTemplate auditDbJdbcTemplate;
        private final DataSource primaryDataSource;

        @Autowired
        public MyDataService(
                @Qualifier("mainDbJdbcTemplate") JdbcTemplate mainDbJdbcTemplate,
                @Qualifier("auditDbJdbcTemplate") JdbcTemplate auditDbJdbcTemplate,
                DataSource primaryDataSource // This will be mainDbDataSource due to @Primary
        ) {
            this.mainDbJdbcTemplate = mainDbJdbcTemplate;
            this.auditDbJdbcTemplate = auditDbJdbcTemplate;
            this.primaryDataSource = primaryDataSource;
        }

        public String getMainData() {
            return this.mainDbJdbcTemplate.queryForObject("SELECT 'Data from MainDB'", String.class);
        }

        public String getAuditData() {
            return this.auditDbJdbcTemplate.queryForObject("SELECT 'Data from AuditDB'", String.class);
        }

        public DataSource getPrimaryDataSource() {
            return this.primaryDataSource;
        }
    }
    ```

    If you autowire without `@Qualifier`, you will get the beans designated as `@Primary` (based on the `ddk.datasource.primary` property).

## JPA Configuration (Future Consideration)

This starter currently focuses on JDBC-based access (`DataSource`, `JdbcTemplate`, `DataSourceTransactionManager`). For applications requiring JPA with multiple data sources, you would typically need to:
-   Configure separate `EntityManagerFactory` beans for each data source.
-   Configure separate `JpaTransactionManager` beans for each `EntityManagerFactory`.
-   Specify entity package locations for each `EntityManagerFactory`.
This can be complex and might be addressed in future enhancements to this starter or a separate JPA-specific multi-source starter.

```
