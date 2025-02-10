package io.github.ddk.core.response;

import io.github.ddk.core.exception.ErrorCode;
import lombok.Data;

/**
 * 统一 API 响应结果封装
 *
 * @author Elijah Du
 * @date 2025/2/8
 */
@Data
public class ApiResponse<T> {

    private final static String SUCCESS = "SUCCESS";

    private String code;
    private String message;
    private T data;
    private Long timestamp;

    public ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> ofSuccess() {
        return new ApiResponse<>(SUCCESS, "Success", null);
    }

    public static <T> ApiResponse<T> ofSuccess(T data) {
        return new ApiResponse<>(SUCCESS, "Success", data);
    }

    public static <T> ApiResponse<T> ofFailed(ErrorCode code, Object... args) {
        return new ApiResponse<>(code.getCode(), code.getMessage(args), null);
    }
}
