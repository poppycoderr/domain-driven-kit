package com.ddk.core.response;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiResponse：统一响应结构")
class ApiResponseTest {

    @Getter
    @AllArgsConstructor
    enum TestError implements ErrorCode {
        USER_NOT_FOUND("用户不存在：{0}"),
        ;

        private final String message;
    }

    @Test
    @DisplayName("成功响应携带 SUCCESS 码与数据")
    void successCarriesData() {
        ApiResponse<String> response = ApiResponse.ofSuccess("payload");

        assertEquals("SUCCESS", response.getCode());
        assertEquals("Success", response.getMessage());
        assertEquals("payload", response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("无数据的成功响应 data 为 null")
    void successWithoutData() {
        assertNull(ApiResponse.ofSuccess().getData());
        assertEquals("SUCCESS", ApiResponse.ofSuccess().getCode());
    }

    @Test
    @DisplayName("失败响应用错误码与格式化后的消息，data 置空")
    void failureCarriesErrorCode() {
        ApiResponse<String> response = ApiResponse.ofFail(TestError.USER_NOT_FOUND, 42);

        assertEquals("USER_NOT_FOUND", response.getCode());
        assertEquals("用户不存在：42", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("时间戳在构造时生成")
    void timestampGeneratedAtConstruction() {
        long before = System.currentTimeMillis();
        ApiResponse<String> response = ApiResponse.ofSuccess("x");
        long after = System.currentTimeMillis();

        assertTrue(response.getTimestamp() >= before);
        assertTrue(response.getTimestamp() <= after);
    }
}
