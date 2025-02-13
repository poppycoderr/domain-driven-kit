package io.github.ddk.web.handler;

import io.github.ddk.core.exception.BusinessException;
import io.github.ddk.core.exception.SystemException;
import io.github.ddk.core.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 处理 {@link SystemException} {@link BusinessException} 异常
 *
 * @author Elijah Du
 * @date 2025/2/13
 */
@Slf4j
public class BaseExceptionHandler {

    @ResponseBody
    @ExceptionHandler(SystemException.class)
    public ApiResponse<Void> handleSystemException(SystemException e) {
        log.error("SystemException: {}", e.getMessage());
        return ApiResponse.ofFail(e.getErrorCode());
    }

    @ResponseBody
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.error("BusinessException: {}", e.getMessage());
        return ApiResponse.ofFail(e.getErrorCode());
    }
}
