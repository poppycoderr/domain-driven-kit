# DDK MyBatis Starter

MyBatis-Plus set up the way a DDD project needs it: pagination, optimistic locking, a guard against full-table updates and deletes, snowflake IDs, audit fields, and optional multi-tenancy and row-level data scopes. It also registers the `MapperProvider` that `GenericRepositoryImpl` needs.

## In 30 seconds

1. Add the dependency, a JDBC driver and a data source:

   ```xml
   <dependency>
       <groupId>com.domaindrivenkit</groupId>
       <artifactId>ddk-mybatis-starter</artifactId>
   </dependency>
   ```

   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/app
       username: app
       password: example_password
   ddk:
     mybatis:
       db-type: mysql          # the pagination dialect; change it for PostgreSQL, H2 and others
   ```

2. Declare your mappers with `@MapperScan` or `@Mapper`, and extend `GenericRepositoryImpl` for your repositories.
3. Check it: save an aggregate twice from two stale copies. The second save fails with `ConcurrentUpdateException` (optimistic locking), and `mapper.delete(null)` is refused (full-table guard).

Use this starter, not just `ddk-mybatis`, whenever you use the generic repository: `MapperProvider` lives in a jar that your component scan does not reach.

## What it does

| Feature | Notes |
|---|---|
| Pagination | `PaginationInnerInterceptor`; dialect, page size limit and overflow behavior configurable |
| Optimistic locking | On by default. A `@Version` field on the PO adds `WHERE version = ?` to updates; `AggregateRoot.version()` and `ConcurrentUpdateException` depend on it |
| Full-table guard | On by default. `update` / `delete` without `WHERE` throws |
| Snowflake IDs | For `@TableId(type = IdType.ASSIGN_ID)`; worker and data center IDs configurable |
| Audit fields | `createTime`, `updateTime`, `createBy`, `updateBy` filled on insert and update from `OperatorContext` |
| Multi-tenancy | Optional. Every statement gets the tenant condition; a statement without a tenant is refused |
| Data scope | Optional. Reads, updates and deletes on listed tables are narrowed to the operator's groups or own rows; without an operator they are refused |

The interceptor order is fixed: tenant, data scope, optimistic locking, full-table guard, pagination. Pagination comes last, so the count query carries the tenant and data scope conditions too. MyBatis itself (`SqlSessionFactory`, mapper scanning, `mybatis-plus.*`) is set up by MyBatis-Plus's own starter.

### Audit fields

```java
@TableField(fill = FieldFill.INSERT)
private Long createBy;

@TableField(fill = FieldFill.INSERT_UPDATE)
private Long updateBy;

@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;
```

Names are fixed. Times are `LocalDateTime`; operators are `String` or `Long`. `createBy` is only filled when empty, so a value you set during a data migration is kept. Without an operator both operator fields stay empty.

### Multi-tenancy

```yaml
ddk:
  mybatis:
    tenant:
      enabled: true
      column: tenant_id
      ignore-tables: [ t_dict, t_tenant ]
```

The tenant comes from the operator in `OperatorContext`, which the [web starter](../ddk-web-starter) sets per request through your `OperatorResolver`. Jobs and message listeners set it with `OperatorContext.runAs(Operator.of("system", tenantId), ...)`. A single statement that has to cross tenants is marked with `@InterceptorIgnore(tenantLine = "true")`.

### Data scope

```yaml
ddk:
  mybatis:
    data-scope:
      enabled: true
      tables: [ t_contract, t_invoice ]
```

```java
@Bean
DataScopeResolver dataScopeResolver(RoleService roles) {
    return operator -> roles.dataScopeOf(operator.id());   // cache this: it runs for every statement on a listed table
}
```

Statements on the listed tables get `(dept_id IN (...) OR create_by = ?)`. `DataScope.all()` leaves them unchanged and `DataScope.none()` adds `1 = 0`. Inserts are not restricted. Mark a single statement with `@InterceptorIgnore(dataPermission = "true")` to exempt it.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `ddk.mybatis.db-type` | `mysql` | Pagination dialect |
| `ddk.mybatis.max-page-size` | `500` | Largest page; bigger requests are cut to this size without an error |
| `ddk.mybatis.overflow` | `false` | Go back to the first page when the page number is past the end |
| `ddk.mybatis.optimistic-locker` | `true` | Optimistic locking interceptor |
| `ddk.mybatis.block-attack` | `true` | Full-table guard |
| `ddk.mybatis.worker-id`, `ddk.mybatis.datacenter-id` | none | Snowflake worker and data center IDs (0–31), set both, unique per instance. Without them the identity is derived from the MAC address and the process ID; in containers, where the process ID is usually 1, only 32 identities remain, and two of seven instances collide with a probability of about one half. Startup logs a warning when they are not set |
| `ddk.mybatis.tenant.enabled` | `false` | Multi-tenancy |
| `ddk.mybatis.tenant.column` | `tenant_id` | Tenant column |
| `ddk.mybatis.tenant.numeric-id` | `true` | `false` compares the tenant as a string |
| `ddk.mybatis.tenant.ignore-tables` | empty | Tables without tenants, case-insensitive |
| `ddk.mybatis.data-scope.enabled` | `false` | Row-level data scope |
| `ddk.mybatis.data-scope.tables` | empty | Tables it applies to, case-insensitive |
| `ddk.mybatis.data-scope.group-column` | `dept_id` | Column with the group a row belongs to |
| `ddk.mybatis.data-scope.owner-column` | `create_by` | Column with the user who created a row |
| `ddk.mybatis.data-scope.numeric-group-id`, `numeric-owner-id` | `true` | Whether those columns are numeric |
| `ddk.mybatis.data-scope.columns.<table>.*` | empty | The four settings above for one table; an empty column name means the table lacks that dimension |

`MapperProvider`, `IdentifierGenerator`, `MybatisPlusInterceptor` and `MetaObjectHandler` are all `@ConditionalOnMissingBean`. Declaring your own `MybatisPlusInterceptor` replaces the whole chain, so tenant, data scope, locking, guard and pagination must then be added by you.

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| `Multi-tenancy is enabled but OperatorContext has no tenant...` | A statement ran without an operator or without a tenant, typically in a job, a listener or a new thread | Wrap the work in `OperatorContext.runAs(...)`, list the table in `ignore-tables`, or mark the mapper method `@InterceptorIgnore(tenantLine = "true")` |
| Startup fails: `ddk.mybatis.data-scope.enabled is true but there is no DataScopeResolver bean` | Data scope turned on without a resolver | Declare a `DataScopeResolver` bean |
| `Audit field createBy on ... must be String or Long` | The operator field has another type | Change the field to `String` or `Long` |
| Audit times stay `null` | The field is a `Date`, or named differently, such as `gmtCreate` | Use `LocalDateTime` and the fixed names |
| Pages are smaller than requested | `max-page-size` cuts larger pages silently | Raise `ddk.mybatis.max-page-size` or page through the results |
| Wrong SQL for `LIMIT` / `OFFSET` on PostgreSQL or H2 | `db-type` is still `mysql` | Set `ddk.mybatis.db-type` |
| `ConcurrentUpdateException` on an update you expected to succeed | The aggregate was loaded before another write, or the row is outside the operator's data scope, which also updates 0 rows | Reload and retry, or check the operator's data scope |
| Now and then a duplicate key error on insert, only with several instances | Two instances derived the same snowflake identity (the startup warning says so) and generated the same ID in the same millisecond | Set `ddk.mybatis.worker-id` and `ddk.mybatis.datacenter-id`, unique per instance, for example from the pod ordinal |
| `MapperProvider` bean not found when the application starts | Only `ddk-mybatis` is on the classpath | Depend on `ddk-mybatis-starter` |
