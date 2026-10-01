package com.ddk.web.openapi;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.ErrorCode;
import com.ddk.core.response.ApiResponse;
import com.ddk.web.config.WebOpenApiAutoConfiguration;
import com.ddk.web.internal.ErrorContractOpenApiCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 断言的是 springdoc 实际生成的文档，而不是注册了哪个 Bean。
 */
@DisplayName("接口文档里的错误约定")
@SpringBootTest(classes = WebOpenApiIntegrationTest.TestApplication.class)
class WebOpenApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("成功响应按泛型展开，每个接口补上 400 / 409 / 500")
    void operationsDocumentSuccessAndErrorResponses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/orders/{id}'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/ApiResponseOrderView"))
                .andExpect(jsonPath("$.components.schemas.ApiResponseOrderView.properties.data['$ref']").value("#/components/schemas/OrderView"))
                .andExpect(jsonPath("$.paths['/orders/{id}'].get.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/orders/{id}'].get.responses['409'].description", containsString("AGGREGATE_BUSY")))
                .andExpect(jsonPath("$.paths['/orders/{id}'].get.responses['500'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"));
    }

    @Test
    @DisplayName("接口自己声明的响应不被覆盖")
    void declaredResponsesAreKept() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/orders/{id}/cancel'].post.responses['409'].description").value("订单已发货，不能取消"))
                .andExpect(jsonPath("$.paths['/orders/{id}/cancel'].post.responses['400'].description", containsString("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("错误码清单汇总框架与应用的枚举，带消息")
    void errorCodesAreCollected() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ErrorCode.enum",
                        hasItems("VALIDATION_ERROR", "CONCURRENT_UPDATE", "AGGREGATE_BUSY", "SYSTEM_ERROR", "ORDER_NOT_FOUND", "ORDER_SHIPPED")))
                .andExpect(jsonPath("$.components.schemas.ErrorCode.description", containsString("| `ORDER_NOT_FOUND` | 订单不存在：{0} |")))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code['$ref']").value("#/components/schemas/ErrorCode"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.timestamp.type").value("string"));
    }

    @Test
    @DisplayName("关闭 Long 转字符串后，时间戳在文档里是 int64")
    void timestampFollowsTheJacksonSetting() {
        OpenAPI openApi = new OpenAPI();
        new ErrorContractOpenApiCustomizer(() -> Map.of("A", "a | b"), false).customise(openApi);

        Schema<?> error = openApi.getComponents().getSchemas().get("ApiErrorResponse");
        assertThat(((Schema<?>) error.getProperties().get("timestamp")).getFormat()).isEqualTo("int64");
        assertThat(openApi.getComponents().getSchemas().get("ErrorCode").getDescription()).contains("| `A` | a \\| b |");
    }

    @Test
    @DisplayName("ddk.web.openapi=false 时不注册")
    void canBeDisabled() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebOpenApiAutoConfiguration.class));

        runner.run(ctx -> assertThat(ctx).hasSingleBean(GlobalOpenApiCustomizer.class));
        runner.withPropertyValues("ddk.web.openapi=false").run(ctx -> assertThat(ctx).doesNotHaveBean(GlobalOpenApiCustomizer.class));
    }

    enum OrderError implements ErrorCode {
        ORDER_NOT_FOUND("订单不存在：{0}"),
        ORDER_SHIPPED("订单已发货，不能取消");

        private final String message;

        OrderError(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    record OrderView(
            Long id,

            String status
    ) {
    }

    @RestController
    static class OrderController {

        @GetMapping("/orders/{id}")
        ApiResponse<OrderView> get(@PathVariable("id") Long id) {
            throw new BusinessException(OrderError.ORDER_NOT_FOUND, id);
        }

        @PostMapping("/orders/{id}/cancel")
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "已取消")
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "订单已发货，不能取消")
        ApiResponse<Void> cancel(@PathVariable("id") Long id) {
            return ApiResponse.ofSuccess();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        OrderController orderController() {
            return new OrderController();
        }
    }
}
