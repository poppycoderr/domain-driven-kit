package com.ddk.web.handler;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.CommonError;
import com.ddk.core.exception.SystemException;
import com.ddk.core.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把异常翻译成统一的 {@link ApiResponse}。
 *
 * <h2>为什么要显式处理 Spring MVC 自己的异常</h2>
 * 只写一个 {@code @ExceptionHandler(Exception.class)} 兜底，会把 MVC 自己抛出的
 * 405（方法不支持）、415（媒体类型不支持）、404 统统压成 500——调用方看到的状态码
 * 和真实原因对不上，排错时会往完全错误的方向查。
 * <p>
 * Spring 6 里这些异常都实现了 {@link ErrorResponse}，自带正确的状态码，
 * 所以单独接一个处理器，沿用它们自己的状态码，只把响应体换成 {@code ApiResponse}。
 *
 * <h2>兜底处理器不暴露原始异常信息</h2>
 * {@code e.getMessage()} 里可能带着 SQL 片段、文件路径、内部类名。
 * 日志里记全量，响应里只给一句通用文案。
 *
 * @author Elijah Du
 * @date 2025/2/13
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class BaseExceptionHandler {

    /**
     * 业务异常：可预期的规则拒绝，用 400。
     * <p>
     * 这类异常不打 error 日志——它们是正常的业务流程分支，不是故障。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException [{}]: {}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.ofFail(e.getErrorCode(), e.getArgs()));
    }

    /**
     * 系统异常：依赖不可用、配置错误一类的故障，用 500。
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(SystemException e) {
        log.error("SystemException [{}]: {}", e.getErrorCode().getCode(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.ofFail(e.getErrorCode(), e.getArgs()));
    }

    /**
     * 请求体上的 {@code @Valid} 校验失败。
     * <p>
     * {@code MethodArgumentNotValidException} 是 {@code BindException} 的子类，
     * 表单绑定失败也走这里。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(BaseExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.ofError(CommonError.VALIDATION_ERROR, message));
    }

    /**
     * 方法参数或路径变量上的 {@code @Validated} 校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(BaseExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.ofError(CommonError.VALIDATION_ERROR, message));
    }

    /**
     * 路径变量或查询参数无法转换成目标类型，例如 {@code /users/abc} 匹配到了 {@code /users/{id}}。
     * 它不实现 {@link ErrorResponse}，不单独处理会落到兜底变成 500。
     */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(TypeMismatchException e) {
        String name = e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : e.getPropertyName();
        Class<?> requiredType = e.getRequiredType();
        String message = name + ": must be " + (requiredType == null ? "a valid value" : requiredType.getSimpleName());
        log.warn("Type mismatch: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.ofError(CommonError.VALIDATION_ERROR, message));
    }

    /**
     * 请求体解析失败，通常是 JSON 语法错误或类型对不上。
     * <p>
     * 不把解析器的原始消息透出去——它会暴露字段类型和内部类名。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.ofFail(CommonError.MALFORMED_REQUEST));
    }

    /**
     * 兜底。
     * <p>
     * Spring MVC 自己抛出的 405、415、404 等异常都实现了 {@link ErrorResponse}，
     * 自带正确的状态码。它们没有共同的 Throwable 父类，所以在这里按接口判断，
     * 沿用各自的状态码，只把响应体换成 {@code ApiResponse}——
     * 否则这些异常会被压成 500，调用方看到的状态码和真实原因对不上。
     * <p>
     * 真正没预料到的异常才走 500，且响应里只给通用文案。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            log.warn("{} {}: {}", status.value(), status.getReasonPhrase(), e.getMessage());
            return ResponseEntity.status(status)
                    .body(new ApiResponse<>(status.name(), status.getReasonPhrase(), null));
        }
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.ofFail(CommonError.SYSTEM_ERROR));
    }

    private static String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static String describe(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
