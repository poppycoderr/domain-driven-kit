package com.example.app.domain.error;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户域错误码。
 * <p>
 * 错误码按<b>领域</b>划分而不是按层划分：同一个「用户不存在」，
 * 无论从哪一层抛出都是同一个码。消息里的 {@code {0}} 是
 * {@code MessageFormat} 占位符，由 {@code ErrorCode.getMessage(args)} 填充。
 *
 * @author Elijah Du
 */
@Getter
@AllArgsConstructor
public enum UserError implements ErrorCode {

    USER_NOT_FOUND("用户不存在：{0}"),
    INVALID_GENDER("非法的性别取值：{0}"),
    ;

    private final String message;
}
