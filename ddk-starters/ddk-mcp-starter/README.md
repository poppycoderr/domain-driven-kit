# DDK MCP Starter

Expose application use cases as [Model Context Protocol](https://modelcontextprotocol.io) tools, so AI agents can call them the same way a browser calls your REST API. The MCP server and `@McpTool` discovery come from Spring AI; this starter adds the conventions a DDD service needs.

```text
AI agent ──MCP──► adapter.mcp.*Tools ──► application service ──► domain
                      │
                      └─ DDK: validate arguments · map exceptions to error codes · hide internals · audit log
```

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-mcp-starter</artifactId>
</dependency>
```

Put tools in the adapter layer and call application services, exactly like a controller:

```java
@Component
@RequiredArgsConstructor
public class UserMcpTools {

    private final UserService userService;

    @McpTool(name = "get_user", description = "Get a user by ID")
    public UserResponse get(@McpToolParam(description = "User ID") @Positive long id) {
        return userService.get(id);
    }
}
```

The server listens on `/mcp` over streamable HTTP.

## What the starter adds

| Behavior | Details |
|---|---|
| Streamable HTTP by default | Spring AI falls back to the deprecated SSE transport when `spring.ai.mcp.server.protocol` is unset; DDK sets `STREAMABLE` at the lowest precedence, so any explicit value wins |
| Argument validation | Bean Validation constraints on `@McpTool` parameters are checked before the tool runs, without `@Validated` on the class. Failures return `VALIDATION_ERROR: id: must be greater than 0` |
| Error codes | `BusinessException` and `SystemException` become tool errors that start with their code, e.g. `USER_NOT_FOUND: 用户不存在：42`, so the model can decide whether to retry |
| No leaked internals | Any other exception returns `SYSTEM_ERROR: 服务器内部错误`; the original message and stack trace go to the log only |
| Audit log | One line per call: `MCP tool [get_user] -> USER_NOT_FOUND in 3 ms`. Argument values are not logged |

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.mcp.enabled` | `true` | Intercept `@McpTool` methods with the behaviors above |
| `ddk.mcp.validation` | `true` | Validate tool arguments |
| `ddk.mcp.audit-log` | `true` | Log one line per tool call |
| `spring.ai.mcp.server.*` | Spring AI | Server name, version, endpoint, protocol |

## Architecture rule

`CommonArchRules.MCP_TOOLS_MUST_RESIDE_IN_ADAPTER` keeps `@McpTool` methods in `..adapter..` packages. Combined with the layered rule, tools can only reach the domain through application services, never repositories.

## Not covered

- **Authentication and authorization**: secure `/mcp` with Spring Security like any other endpoint
- **Reactive servers**: only the servlet (WebMVC) stack is supported
- **Tool argument values in logs**: deliberately omitted; add your own logging where the data is not sensitive
