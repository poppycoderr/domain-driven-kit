package com.example.user.adapter.controller.request;

import com.example.user.application.command.RegisterUserCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册用户请求。这里的校验只拦明显不合法的输入，业务不变量由领域模型保证。
 */
public record RegisterUserRequest(

        @NotBlank
        @Size(min = 4, max = 20)
        String username,

        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "must contain letters and digits, 8 to 64 characters")
        String password,

        @NotNull
        Integer gender,

        @NotBlank
        String phoneNumber,

        String email
) {

    public RegisterUserCommand toCommand() {
        return new RegisterUserCommand(username, password, gender, phoneNumber, email);
    }
}
