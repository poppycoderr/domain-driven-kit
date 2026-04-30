package com.ddk.web.handler;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.SystemException;
import com.ddk.core.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 处理 {@link SystemException}、{@link BusinessException} 以及参数校验异常。
 *
 * @author Elijah Du
 * @date 2025/2/13
 */
@Slf4j
public class BaseExceptionHandler {

    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(SystemException.class)
    public ApiResponse<Void> handleSystemException(SystemException e) {
        log.error("SystemException: {}", e.getMessage(), e);
        return ApiResponse.ofFail(e.getErrorCode());
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        return ApiResponse.ofFail(e.getErrorCode());
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return new ApiResponse<>("VALIDATION_ERROR", message, null);
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Bind failed: {}", message);
        return new ApiResponse<>("VALIDATION_ERROR", message, null);
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected exception: {}", e.getMessage(), e);
        return new ApiResponse<>("SYSTEM_ERROR", "Internal Server Error", null);
    }
}
