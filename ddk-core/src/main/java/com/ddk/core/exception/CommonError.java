package com.ddk.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 框架级通用错误码。
 * <p>
 * 这些码由 DDK 自己抛出或由全局异常处理器兜底使用，业务错误码请在各自的领域里定义——
 * 错误码应当按<b>领域</b>划分，而不是集中到一个巨大的枚举里。
 * <p>
 * 码值是稳定契约：前端会按码分支，改名等于破坏接口。
 *
 * @author Elijah Du
 */
@Getter
@AllArgsConstructor
public enum CommonError implements ErrorCode {

    /** 请求参数未通过校验。具体哪个字段不合法由处理器在运行期填入 */
    VALIDATION_ERROR("请求参数校验失败"),

    /** 请求体无法解析，通常是 JSON 格式错误 */
    MALFORMED_REQUEST("请求体格式不正确"),

    /** 兜底：未被任何处理器识别的异常。对外不暴露原始异常信息 */
    SYSTEM_ERROR("服务器内部错误"),
    ;

    private final String message;
}
