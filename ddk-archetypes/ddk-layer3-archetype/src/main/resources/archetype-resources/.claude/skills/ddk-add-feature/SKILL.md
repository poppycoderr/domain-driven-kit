---
name: ddk-add-feature
description: Add a business feature to this DDK three-layer service - entity, repository contract and implementation, PO, mapper, converters, business service, request, response and controller endpoint. Use when the user asks for a new capability such as "manage coupons" or "add an endpoint to freeze an account".
---
#set( $h2 = '##' )

# 新增业务功能

以「优惠券 Coupon」为例，根包 `${package}`。已有实体时跳过第 1、3 步，只补用例。

$h2 1. 业务模型（business.model / error / acl）

| 文件 | 要点 |
|---|---|
| `business/model/Coupon.java` | 可继承 `AggregateRoot<CouponId>`；私有构造器；创建工厂方法校验规则并登记事件，重建工厂方法不登记；状态变更方法守卫规则；不写公开 setter |
| `business/model/CouponId.java` | `final class CouponId extends Identifier<Long>`，提供 `static of(Long)` |
| `business/error/CouponError.java` | `enum CouponError implements ErrorCode` |
| `business/acl/CouponRepository.java` | `interface CouponRepository extends GenericRepository<Coupon, Long>` |

$h2 2. 业务服务（business.command / response / service）

- `*Command` 与 `*Query` 用 record；分页查询继承 `PageQuery`。
- `CouponResponse` 用 record，提供 `static from(Coupon)`。
- `CouponService`：`@Service`，写操作加 `@Transactional`；取实体 → 调用实体方法 → 保存 → 转响应。只依赖 `CouponRepository` 接口，不注入 `CouponMapper` 或任何实现类。

$h2 3. 基础设施（infrastructure）

| 文件 | 要点 |
|---|---|
| `infrastructure/orm/po/CouponPO.java` | `@Data`，`@TableId(type = IdType.ASSIGN_ID)`，`@Version private Long version` |
| `infrastructure/orm/mapper/CouponMapper.java` | `@Mapper interface CouponMapper extends BaseMapper<CouponPO>` |
| `infrastructure/converter/*Converter.java` | 两个方向各一个：`@Component` + `@EnhancedMapper(source = ..., target = ...)` 实现 `ObjectMapper` |
| `infrastructure/acl/impl/CouponRepositoryImpl.java` | `@Repository`，`extends GenericRepositoryImpl<Coupon, Long, CouponPO, CouponMapper> implements CouponRepository` |

在建表脚本中加表：主键 `BIGINT`、`version BIGINT NOT NULL DEFAULT 0`、`create_time`、`update_time`。

$h2 4. 接口（adapter.controller）

- `request/*Request.java`：record + Bean Validation 注解 + `toCommand()`。
- `CouponController`：`@Valid @RequestBody`、`@PathVariable("id")`、返回 `ApiResponse<T>`；只调用 `CouponService`。

$h2 5. 测试与验证

- 实体规则写纯单元测试；接口用 `@SpringBootTest` + `@AutoConfigureMockMvc` 覆盖参数绑定与错误码。
- 运行 `mvn verify`，`ArchitectureTest` 必须通过。
