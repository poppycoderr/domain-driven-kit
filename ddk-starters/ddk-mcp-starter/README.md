# DDK MCP Starter

Expose application use cases as [Model Context Protocol](https://modelcontextprotocol.io) tools, so AI agents can call them the same way a browser calls your REST API. The MCP server and `@McpTool` discovery come from Spring AI; this starter adds the conventions a DDD service needs.

```text
AI agent ──MCP──► adapter.mcp.*Tools ──► application service ──► domain
                      │
                      └─ DDK: validate arguments · map exceptions to error codes · hide internals · audit log
```

## In 30 seconds

1. Add the dependency:

   ```xml
   <dependency>
       <groupId>com.domaindrivenkit</groupId>
       <artifactId>ddk-mcp-starter</artifactId>
   </dependency>
   ```

2. Put a method annotated with `@McpTool` on a bean in the adapter layer, as shown under [Usage](#usage).
3. Check it: connect an MCP client, such as the MCP Inspector, to `http://localhost:8080/mcp` over streamable HTTP; `tools/list` shows the tool.

## Usage

```xml
<dependency>
    <groupId>com.domaindrivenkit</groupId>
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
| Cancellation | A tool whose thread is interrupted, for example while it waits for an aggregate lock during shutdown, returns `REQUEST_CANCELLED: 请求被取消，请稍后重试` |
| No leaked internals | Any other exception returns `SYSTEM_ERROR: 服务器内部错误`; the original message and stack trace go to the log only |
| Audit log | One line per call: `MCP tool [get_user] -> USER_NOT_FOUND in 3 ms`. Argument values are not logged |

## Operator and tenant

MCP calls arrive at `/mcp` as ordinary HTTP requests, so `ddk-web-starter`'s `OperatorContextFilter`, or your own filter, sets `OperatorContext` for them. With the default synchronous server and the streamable HTTP transport, the tool runs on the request thread and sees that operator, including the tenant, and the filter clears it when the request ends. `McpOperatorContextTest` pins this behavior. An asynchronous server (`spring.ai.mcp.server.type=ASYNC`) runs tools on other threads and does not get the context.

The starter does not decide who may call which tool; see [Not covered](#not-covered).

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

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| The tool does not appear in `tools/list` | The class is not a Spring bean, or the method is not annotated with `@McpTool` | Annotate the class with `@Component` and the method with `@McpTool` |
| An old client cannot connect | DDK defaults to streamable HTTP; older clients only speak SSE | Upgrade the client, or set `spring.ai.mcp.server.protocol` explicitly |
| A tool returns `SYSTEM_ERROR: 服务器内部错误` | An unexpected exception; the details are only in the log | Read the log; throw `BusinessException` with an error code for expected failures |
| A tool returns `VALIDATION_ERROR: ...` | A Bean Validation constraint on a parameter failed | Send a valid argument; the message names the parameter |
| A tool sees no operator or tenant | The server is asynchronous (`spring.ai.mcp.server.type=ASYNC`) and runs tools on other threads | Use the default synchronous server |
