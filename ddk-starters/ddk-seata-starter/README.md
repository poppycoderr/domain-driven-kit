# DDK Seata Starter

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

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
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
