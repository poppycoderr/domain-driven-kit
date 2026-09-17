package com.example.user.application.response;

import com.example.user.domain.model.entity.User;
import com.example.user.domain.model.valueobject.Email;

/**
 * 用户对外响应。手机号只返回脱敏值，这也是不把领域对象直接交给 Jackson 的原因。
 */
public record UserResponse(

        Long id,

        String username,

        String gender,

        String phoneNumber,

        String email,

        boolean enabled
) {

    public static UserResponse from(User user) {
        Email email = user.email();
        return new UserResponse(user.id().value(), user.username(), user.gender().getDescription(), user.phoneNumber().masked(),
                email == null ? null : email.value(), user.enabled());
    }
}
