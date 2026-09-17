package com.ddk.core.response;

import com.ddk.core.exception.ErrorCode;
import lombok.Data;
import org.jspecify.annotations.Nullable;

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
    private @Nullable T data;
    private Long timestamp;

    public ApiResponse(String code, String message, @Nullable T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> ofSuccess() {
        return new ApiResponse<>(SUCCESS, "Success", null);
    }

    public static <T> ApiResponse<T> ofSuccess(@Nullable T data) {
        return new ApiResponse<>(SUCCESS, "Success", data);
    }

    public static <T> ApiResponse<T> ofFail(ErrorCode code, Object... args) {
        return new ApiResponse<>(code.getCode(), code.getMessage(args), null);
    }

    /**
     * 用调用方给定的文案，而不是错误码自带的模板。
     * <p>
     * 用于消息内容来自运行期的场景——典型的是参数校验，具体哪个字段不合法
     * 要到运行时才知道。这类文案<b>不能</b>走 {@link ErrorCode#getMessage(Object...)}：
     * 它内部是 {@code MessageFormat}，文案里的单引号会被当成转义符吃掉，
     * 花括号则会被当成占位符。
     */
    public static <T> ApiResponse<T> ofError(ErrorCode code, String message) {
        return new ApiResponse<>(code.getCode(), message, null);
    }
}
