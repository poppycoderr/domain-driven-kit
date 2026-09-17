# DDK DB Starter

Registers several JDBC data sources from configuration. Each source gets its own `DataSource`, transaction manager and `JdbcTemplate`; one of them is primary.

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-db-starter</artifactId>
</dependency>
```

```yaml
ddk:
  datasource:
    primary: main                 # optional with a single source
    sources:
      main:
        url: jdbc:mysql://localhost:3306/main
        username: app
        password: secret
        pool:                     # bound onto the pool instance
          maximum-pool-size: 20
          pool-name: main-pool
      audit:
        url: jdbc:mysql://localhost:3306/audit
        username: app
        password: secret
```

Beans per source, named after its key:

| Bean | Type | Primary |
|---|---|---|
| `<name>DataSource` | pool from `type`, HikariCP by default | when `<name>` is primary |
| `<name>TransactionManager` | `JdbcTransactionManager` | when `<name>` is primary |
| `<name>JdbcTemplate` | `JdbcTemplate` | when `<name>` is primary |

Inject the non-primary ones by name:

```java
@Transactional(transactionManager = "auditTransactionManager")
public void record(@Qualifier("auditJdbcTemplate") JdbcTemplate audit) { ... }
```

## Configuration

| Property | Description |
|---|---|
| `ddk.datasource.primary` | Name of the primary source. Required when more than one source is configured |
| `ddk.datasource.sources.<name>.url` | JDBC URL, required |
| `ddk.datasource.sources.<name>.username` / `password` | Credentials |
| `ddk.datasource.sources.<name>.driver-class-name` | Usually inferred from the URL |
| `ddk.datasource.sources.<name>.type` | `DataSource` implementation |
| `ddk.datasource.sources.<name>.pool.*` | Properties of the pool itself, e.g. HikariCP's `maximum-pool-size`, `connection-timeout` |

## Behaviour

- Active only when `ddk.datasource.sources` is set; otherwise `spring.datasource.*` works as usual.
- Runs before Spring Boot's `DataSourceAutoConfiguration`, so Boot's single data source, transaction manager and `JdbcTemplate` back off.
- Startup fails immediately when several sources have no `primary`, when `primary` names no source, or when a source has no `url`.
- This starter does not route between data sources at runtime, and MyBatis-Plus binds to the primary data source only.
