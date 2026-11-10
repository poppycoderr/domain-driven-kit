# DDK Seata Starter

> **Deprecated since 0.12.0.** 1.0 removes this starter from the BOM and stops publishing it. To keep using it, pin 0.12.x or copy its three classes into your project.

Seata distributed transactions for DDK services, plus one DDK addition: outbound HTTP calls carry the global transaction ID (XID), so a downstream service joins the same global transaction.

```text
order-service                                   inventory-service
  @GlobalTransactional placeOrder                 
    RootContext XID = 10.0.0.1:8091:42            
    restClient.post("/deduct")                    
      XidPropagationInterceptor (DDK)  ── TX_XID ──► Seata inbound interceptor
                                                     RootContext.bind(XID)
                                                     deduct() joins the global transaction
```

Seata binds the XID from inbound requests but adds nothing to outbound ones. Without this, the downstream service commits on its own and a global rollback cannot undo it.

## In 30 seconds

1. Add the dependency:

   ```xml
   <dependency>
       <groupId>com.domaindrivenkit</groupId>
       <artifactId>ddk-seata-starter</artifactId>
   </dependency>
   ```

2. Configure Seata with its own `seata.*` properties, and build HTTP clients from the injected `RestClient.Builder` or `RestTemplateBuilder`.
3. Check it: inside a `@GlobalTransactional` method, the outgoing HTTP request carries the `TX_XID` header and the downstream service joins the same global transaction.

**Deprecated since 0.12.0; 1.0 will not publish it.** It has wiring tests only, Seata's runtime has not been verified on Spring Boot 4, and global transactions go against the outbox and eventual consistency that DDK builds on. To keep using it, pin 0.12.x or copy its three classes into your project.

## Usage

```xml
<dependency>
    <groupId>com.domaindrivenkit</groupId>
    <artifactId>ddk-seata-starter</artifactId>
</dependency>
```

Seata itself is configured with its own `seata.*` properties (`application-id`, `tx-service-group`, registry and TC address).

Build HTTP clients from the Spring-managed builders so they get the interceptor:

```java
@Bean
RestClient inventoryClient(RestClient.Builder builder) {
    return builder.baseUrl("http://inventory-service").build();
}
```

A `new RestTemplate()` or `RestClient.create()` bypasses the builders and does not propagate the XID.

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.seata.http-propagation.enabled` | `true` | Add the `TX_XID` header to outbound `RestClient` / `RestTemplate` calls |
| `seata.enabled` | `true` | Seata's own switch; `false` also disables the DDK additions |

## Behaviour

- The header is added only inside a global transaction, and a `TX_XID` header set explicitly by the caller is kept.
- `spring-web` is optional; without it nothing HTTP-related is registered.
- Data source proxying, `@GlobalTransactional` scanning and inbound XID binding come from `seata-spring-boot-starter`.
- Seata 2.6 does not declare Spring Boot 4 support. Its auto-configuration only references Boot APIs that still exist in 4.1, and DDK's own wiring is tested, but Seata's runtime (TC connection, data source proxy) has not been verified against Boot 4.

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| The downstream service does not join the global transaction | The client was created with `new RestTemplate()` or `RestClient.create()`, which bypasses the builders | Build it from the injected `RestClient.Builder` or `RestTemplateBuilder` |
| The application cannot reach the transaction coordinator | `seata.*` registry or TC address is wrong | Check Seata's own configuration |
