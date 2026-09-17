#set( $h2 = '##' )
# ${artifactId}

基于 [Domain Driven Kit](https://github.com/poppycoderr/domain-driven-kit) 四层架构骨架生成的服务。

$h2 运行

```bash
mvn spring-boot:run
```

默认使用内存 H2 数据库，接入真实数据库时修改 `src/main/resources/application.yml` 并引入对应驱动。

$h2 分层

```text
${package}
├── adapter.controller           REST 控制器与 *Request
├── application
│   ├── command / query          用例入参
│   ├── response                 *Response.from(...)
│   ├── service                  应用服务：编排与事务
│   └── handler                  领域事件订阅方
├── domain                       不依赖任何框架
│   ├── model                    聚合根、标识、值对象、枚举
│   ├── event                    领域事件
│   ├── acl                      仓储与外部能力端口
│   ├── service                  领域服务
│   └── error                    领域错误码
└── infrastructure
    ├── acl.impl                 端口实现
    ├── converter                Entity ↔ PO
    └── orm.po / orm.mapper      持久化对象与 Mapper
```

每个包的 `package-info.java` 写明了它放什么。依赖方向由 `ArchitectureTest` 中的 `CommonArchRules.LAYERED_ARCHITECTURE_RULE` 校验，违反即构建失败。

完整的业务示例见 DDK 仓库的 `ddk-examples/ddk-example-user`。
