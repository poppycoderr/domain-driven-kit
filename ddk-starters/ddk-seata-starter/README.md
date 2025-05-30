# DDK Seata Starter

This starter simplifies the integration of Seata for distributed transaction management in DDK applications. It leverages the `seata-spring-boot-starter` to enable Seata's capabilities.

## Features

-   **Simplified Seata Setup:** Integrates Seata's Spring Boot support.
-   **Distributed Transaction Management:** Enables the use of Seata's transaction modes (e.g., AT, TCC, SAGA, XA). This starter primarily facilitates AT mode with JDBC DataSources.
-   **Automatic DataSource Proxying:** For AT mode, DataSources are automatically wrapped with Seata's `DataSourceProxy` if Seata is enabled.
-   **Annotation-Driven:** Use `@GlobalTransactional` to demarcate global transaction boundaries.

## Dependencies

-   `io.seata:seata-spring-boot-starter`
-   `org.springframework.boot:spring-boot-starter-jdbc` (recommended for AT mode)

## Configuration

This starter relies on Seata's native configuration properties. You need to configure them in your `application.properties` or `application.yml`.

**Essential Seata Properties:**

```properties
# Enable Seata (true by default if this starter is included, but explicit is good)
seata.enabled=true

# Your application's unique ID
seata.application-id=your-application-name

# Transaction service group - must match your Seata server configuration
seata.tx-service-group=my_tx_group

# Configure Seata server discovery (using file mode for simplicity here)
# For production, use a discovery service like Nacos, Eureka, Redis, Zookeeper, etcd, Consul.
seata.registry.type=file
seata.config.type=file

# Define the virtual group mapping for your transaction service group.
# 'default' here is the cluster name in Seata server's file.conf (if using file based naming).
seata.service.vgroup-mapping.my_tx_group=default

# List of Seata server instances.
seata.service.grouplist.default=127.0.0.1:8091 # Replace with your Seata server address(es)
```

**Explanation of Key Properties:**

-   `seata.enabled`: Set to `true` to activate Seata.
-   `seata.application-id`: A unique identifier for your application instance.
-   `seata.tx-service-group`: The transaction group your application belongs to. This is crucial for Seata server to manage transactions. It should match a group defined on the Seata server side.
-   `seata.registry.type` / `seata.config.type`: How Seata client discovers TC (Transaction Coordinator) and fetches configurations. Options include `file`, `nacos`, `eureka`, `redis`, `zk`, `consul`, `etcd`, `apollo`. `file` is simplest for local testing.
-   `seata.service.vgroup-mapping.[your-tx-service-group]`: Maps your transaction service group to a Seata server cluster name.
-   `seata.service.grouplist.[cluster-name]`: The actual address(es) of the Seata server instances for a given cluster.

Refer to the [official Seata documentation](https://seata.io/docs/user/configurations/) for a complete list of configuration options.

## How to Use

1.  **Include the starter in your project's `pom.xml`:**

    ```xml
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-seata-starter</artifactId>
        <version>${ddk.version}</version> <!-- Ensure this matches your project's ddk version -->
    </dependency>
    ```
    Ensure you also have a JDBC DataSource configured (e.g., via `spring-boot-starter-jdbc` or the `ddk-db-starter`) if you plan to use AT mode with databases.

2.  **Configure Seata properties** in your `application.properties` or `application.yml` as shown above.

3.  **Annotate your service methods for global transactions:**

    Use `@GlobalTransactional` on methods that initiate a distributed transaction. If you are calling multiple services that participate in the transaction, this annotation on the entry point method of the transaction ensures that all subsequent operations across services (that are Seata-aware) are part of the same global transaction.

    ```java
    import io.seata.spring.annotation.GlobalTransactional;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.jdbc.core.JdbcTemplate;
    import org.springframework.stereotype.Service;

    @Service
    public class OrderService {

        @Autowired
        private JdbcTemplate jdbcTemplate; // Assumes this DataSource is proxied by Seata

        @Autowired
        private AccountService accountService; // Another service

        @Autowired
        private StorageService storageService; // Yet another service

        @GlobalTransactional(timeoutMills = 300000, name = "create-order-tx")
        public void createOrder(String userId, String commodityCode, int count) {
            // 1. Create order record
            jdbcTemplate.update("INSERT INTO orders (user_id, commodity_code, count) VALUES (?, ?, ?)",
                                userId, commodityCode, count);

            // 2. Deduct account balance (simulated call to another service)
            accountService.deductBalance(userId, calculatePrice(commodityCode, count));

            // 3. Deduct storage (simulated call to another service)
            storageService.deductStock(commodityCode, count);

            // If any of these steps fail, the entire transaction should roll back
            // across all participating services, thanks to Seata.
        }

        private int calculatePrice(String commodityCode, int count) {
            // Business logic for price calculation
            return 100 * count; // Example
        }
    }
    ```
    (Note: `AccountService` and `StorageService` would be remote services or local services whose DataSources are also managed by Seata).

## AT Mode and DataSource Proxying

When using Seata's AT (Automatic Transaction) mode with JDBC databases:
-   The `seata-spring-boot-starter` automatically wraps your `javax.sql.DataSource` beans with `io.seata.rm.datasource.DataSourceProxy`.
-   This proxy intercepts SQL executions to record undo logs, enabling automatic rollback in case of transaction failure.
-   Ensure your database tables have primary keys for AT mode to function correctly.

This starter helps ensure that this auto-proxying mechanism is active when Seata is enabled.
```
