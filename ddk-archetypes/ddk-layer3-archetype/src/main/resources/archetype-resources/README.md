#set( $h2 = '##' )
# ${artifactId}

基于 [Domain Driven Kit](https://github.com/poppycoderr/domain-driven-kit) 三层架构骨架生成的服务。

$h2 运行

```bash
mvn spring-boot:run
```

默认使用内存 H2 数据库，接入真实数据库时修改 `src/main/resources/application.yml` 并引入对应驱动（MySQL 还需要 `flyway-mysql`）。

表结构由 Flyway 管理：每次变化在 `src/main/resources/db/migration` 下新增一个 `V<版本号>__<说明>.sql`，启动时自动执行；已执行过的脚本不要修改。

启动时可能看到三条 WARN：

- `No MyBatis mapper was found`：骨架里还没有 Mapper，加了第一个就会消失。
- Flyway 提示 H2 的版本比它验证过的新：H2 的版本由 Spring Boot 管理，本地开发可以忽略。
- `ddk.mybatis.worker-id and ddk.mybatis.datacenter-id are not both set`：单机开发可以忽略。多实例部署时给每个实例设置不同的 `ddk.mybatis.worker-id` 和 `ddk.mybatis.datacenter-id`，否则雪花 ID 可能重复。

接口返回的 JSON 里，`Long` 和 `long` 字段都写成字符串，避免 JavaScript 丢失雪花 ID 的精度；金额、数量这类字段也一样。不需要时设置 `ddk.web.write-long-as-string=false`。参数校验失败的消息随请求的 `Accept-Language` 切换语言（`zh-CN` 时是「不能为null」），业务错误的文案来自错误码枚举，固定是枚举里写的语言。HTTP 状态按异常的类型而定，不按错误码：所有 `BusinessException`（包括 `*_NOT_FOUND` 这类错误码）和参数校验失败都是 400，并发冲突与重复请求是 409，限流是 429，`SystemException` 和未预料的异常是 500；调用方应当按响应里的 `code` 区分错误。完整的对应关系见 DDK Web starter 的 README。项目不带接口文档，需要时加入 `org.springdoc:springdoc-openapi-starter-webmvc-ui`，版本由 DDK 的 BOM 管理，DDK 的 Web starter 会在文档里补上统一的错误响应和错误码。

$h2 分层

```text
${package}
├── adapter.controller           REST 控制器与 *Request
├── business
│   ├── command / query          用例入参
│   ├── response                 *Response.from(...)
│   ├── service                  业务服务：编排与事务
│   ├── model                    实体、值对象、枚举
│   ├── event / handler          业务事件与订阅方
│   ├── acl                      仓储与外部能力接口
│   └── error                    业务错误码
└── infrastructure
    ├── acl.impl                 接口实现
    ├── converter                实体 ↔ PO
    └── orm.po / orm.mapper      持久化对象与 Mapper
```

每个包的 `package-info.java` 写明了它放什么。依赖方向由 `ArchitectureTest` 中的 `CommonArchRules.THREE_LAYER_ARCHITECTURE_RULE` 校验，违反即构建失败。

$h2 AI 协作

`AGENTS.md` 写明了分层职责、编码约定与提交前必须通过的 `mvn verify`，Claude Code、Codex 等 AI 编码代理会自动读取；`CLAUDE.md` 引用了它。`.claude/skills/` 下是按本项目约定编写的 Claude Code Skills。

完整的业务示例见 DDK 仓库的 `ddk-examples/ddk-example-user`。
