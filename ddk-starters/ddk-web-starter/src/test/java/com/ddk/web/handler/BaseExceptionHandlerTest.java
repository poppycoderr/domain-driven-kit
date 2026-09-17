package com.ddk.web.handler;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.ErrorCode;
import com.ddk.core.exception.SystemException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器的行为测试。
 * <p>
 * 断言的是「调用方看到什么状态码和什么响应体」，而不是「注册了哪个 Bean」——
 * 后者通过了也保护不了任何东西。
 */
@DisplayName("全局异常处理器")
class BaseExceptionHandlerTest {

    @Getter
    @AllArgsConstructor
    enum TestError implements ErrorCode {
        USER_NOT_FOUND("用户不存在：{0}"),
        UPSTREAM_UNAVAILABLE("上游服务不可用"),
        ;

        private final String message;
    }

    @Data
    static class CreateRequest {
        @NotBlank(message = "不能为空")
        private String name;

        @Min(value = 1, message = "必须大于 0")
        private int quantity;
    }

    @RestController
    @RequestMapping("/t")
    static class TestController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(TestError.USER_NOT_FOUND, 42);
        }

        @GetMapping("/business-no-args")
        void businessNoArgs() {
            throw new BusinessException(TestError.UPSTREAM_UNAVAILABLE);
        }

        @GetMapping("/system")
        void system() {
            throw new SystemException(TestError.UPSTREAM_UNAVAILABLE);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("连接串是 jdbc:mysql://10.0.0.1/prod?user=root");
        }

        @PostMapping("/create")
        void create(@Valid @RequestBody CreateRequest request) {
        }

        @GetMapping("/items/{id}")
        String item(@PathVariable("id") Long id) {
            return "item";
        }

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new BaseExceptionHandler())
            .build();

    @Test
    @DisplayName("业务异常 -> 400，带上错误码与填充后的消息")
    void businessExceptionBecomes400() throws Exception {
        mvc.perform(get("/t/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("用户不存在：42"));
    }

    @Test
    @DisplayName("不带参数的业务异常也能正常返回，不会因为 MessageFormat 而炸")
    void businessExceptionWithoutArgs() throws Exception {
        mvc.perform(get("/t/business-no-args"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("上游服务不可用"));
    }

    @Test
    @DisplayName("系统异常 -> 500")
    void systemExceptionBecomes500() throws Exception {
        mvc.perform(get("/t/system"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    @DisplayName("方法不支持仍然是 405，不会被兜底处理器压成 500")
    void methodNotAllowedKeepsItsStatus() throws Exception {
        mvc.perform(post("/t/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("媒体类型不支持仍然是 415")
    void unsupportedMediaTypeKeepsItsStatus() throws Exception {
        mvc.perform(post("/t/create").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("请求体校验失败 -> 400，消息里指出是哪个字段")
    void validationFailureListsFields() throws Exception {
        mvc.perform(post("/t/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quantity")));
    }

    @Test
    @DisplayName("路径变量类型不匹配 -> 400，而不是落到兜底变成 500")
    void typeMismatchBecomes400() throws Exception {
        mvc.perform(get("/t/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("id: must be Long"));
    }

    @Test
    @DisplayName("请求体是坏 JSON -> 400，不把解析器的原始消息透出去")
    void malformedBodyBecomes400() throws Exception {
        mvc.perform(post("/t/create").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("未预料的异常 -> 500，响应里不泄漏原始异常信息")
    void unexpectedExceptionDoesNotLeakDetails() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))));
    }

    @Test
    @DisplayName("正常请求不受影响")
    void normalRequestIsUntouched() throws Exception {
        mvc.perform(get("/t/ok")).andExpect(status().isOk());
    }
}
