package com.example.user.domain.error;

import com.ddk.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户域错误码。按领域而不是按层划分，同一个「用户不存在」无论从哪一层抛出都是同一个码。
 */
@Getter
@AllArgsConstructor
public enum UserError implements ErrorCode {

    USER_NOT_FOUND("用户不存在：{0}"),
    USERNAME_TAKEN("用户名已被占用：{0}"),
    INVALID_USERNAME("用户名长度必须在 4 到 20 之间：{0}"),
    INVALID_GENDER("非法的性别取值：{0}"),
    INVALID_PHONE_NUMBER("手机号格式非法：{0}"),
    INVALID_EMAIL("邮箱格式非法：{0}"),
    ;

    private final String message;
}
