package com.ddk.web.internal;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 把 DDK 的错误约定写进 OpenAPI 文档。
 * <p>
 * 全局异常处理器返回的是 {@code ResponseEntity}，springdoc 从它身上推断不出错误响应，文档里的接口因此只有 200。这里补上三类响应
 * （400 业务拒绝与参数校验、409 冲突、500 系统故障），响应体是失败时的 {@code ApiResponse}，其中 {@code code} 的取值是汇总出来的错误码清单。
 * 接口自己已经声明的响应不覆盖。
 */
public final class ErrorContractOpenApiCustomizer implements GlobalOpenApiCustomizer {

    public static final String ERROR_RESPONSE_SCHEMA = "ApiErrorResponse";

    public static final String ERROR_CODE_SCHEMA = "ErrorCode";

    private static final String ERROR_RESPONSE_REF = "#/components/schemas/" + ERROR_RESPONSE_SCHEMA;

    private final Supplier<Map<String, String>> errorCodes;

    private final boolean longAsString;

    public ErrorContractOpenApiCustomizer(Supplier<Map<String, String>> errorCodes, boolean longAsString) {
        this.errorCodes = errorCodes;
        this.longAsString = longAsString;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }
        components.addSchemas(ERROR_CODE_SCHEMA, errorCodeSchema(errorCodes.get()));
        components.addSchemas(ERROR_RESPONSE_SCHEMA, errorResponseSchema());

        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(this::addErrorResponses));
        }
    }

    private void addErrorResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        addIfAbsent(responses, "400", "业务规则拒绝或请求不合法。`code` 是具体的业务错误码，参数校验失败时为 `VALIDATION_ERROR`，请求体无法解析时为 `MALFORMED_REQUEST`");
        addIfAbsent(responses, "409", "冲突：`CONCURRENT_UPDATE` 数据已被其他操作修改，`AGGREGATE_BUSY` 聚合正被其他操作处理，`DUPLICATE_REQUEST` 重复提交");
        addIfAbsent(responses, "500", "系统故障。`code` 为 `SYSTEM_ERROR` 或应用定义的系统错误码，不包含内部细节");
    }

    private static void addIfAbsent(ApiResponses responses, String status, String description) {
        if (responses.containsKey(status)) {
            return;
        }
        Schema<?> reference = new Schema<>().$ref(ERROR_RESPONSE_REF);
        responses.addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(reference))));
    }

    private static Schema<?> errorCodeSchema(Map<String, String> codes) {
        StringBuilder description = new StringBuilder("全部错误码。消息里的 `{0}` 是运行期填入的参数。\n\n| 错误码 | 消息 |\n|---|---|\n");
        codes.forEach((code, message) -> description.append("| `").append(code).append("` | ").append(message.replace("|", "\\|")).append(" |\n"));
        List<String> values = new ArrayList<>(codes.keySet());
        StringSchema schema = new StringSchema();
        schema.setEnum(values);
        schema.setDescription(description.toString());
        return schema;
    }

    private Schema<?> errorResponseSchema() {
        Schema<?> timestamp = longAsString ? new StringSchema() : new IntegerSchema().format("int64");
        return new ObjectSchema()
                .description("失败时的响应体，结构与成功时的 ApiResponse 相同，data 为空")
                .addProperty("code", new Schema<>().$ref("#/components/schemas/" + ERROR_CODE_SCHEMA))
                .addProperty("message", new StringSchema().description("给人看的错误消息"))
                .addProperty("data", new Schema<>().nullable(true).description("失败时为 null"))
                .addProperty("timestamp", timestamp.description("响应时间，毫秒时间戳"))
                .required(List.of("code", "message", "timestamp"));
    }
}
