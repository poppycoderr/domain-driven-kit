package com.example.user.adapter.controller.request;

import com.example.user.application.command.UpdateUserCommand;
import jakarta.validation.constraints.Size;

/**
 * 修改用户资料请求，字段缺省表示不修改。
 */
public record UpdateUserRequest(

        @Size(min = 4, max = 20)
        String username,

        String email
) {

    public UpdateUserCommand toCommand() {
        return new UpdateUserCommand(username, email);
    }
}
