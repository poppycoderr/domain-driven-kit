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

    /** 保存时发现数据已被其他操作修改或删除（乐观锁冲突）。调用方应重新加载后重试 */
    CONCURRENT_UPDATE("数据已被其他操作修改，请刷新后重试：{0}"),

    /** 聚合正被另一个操作独占处理，在等待时间内没有拿到锁。调用方可以稍后重试 */
    AGGREGATE_BUSY("{0} 正在被其他操作处理，请稍后重试"),

    /** 同一个请求已经在处理或已经处理过（重复提交） */
    DUPLICATE_REQUEST("请求正在处理或已处理，请勿重复提交"),

    /** 调用超过了限流阈值。调用方应当降低频率后重试 */
    RATE_LIMITED("请求过于频繁，请稍后再试"),

    /** 兜底：未被任何处理器识别的异常。对外不暴露原始异常信息 */
    SYSTEM_ERROR("服务器内部错误"),
    ;

    private final String message;
}
