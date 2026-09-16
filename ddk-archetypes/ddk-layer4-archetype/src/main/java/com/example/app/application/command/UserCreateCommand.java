package com.example.app.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户创建命令。
 * <p>
 * 命令是<b>应用层</b>的入参，不是领域对象：它可以贫血、可以有 setter、
 * 可以带 Bean Validation 注解。这里的校验拦的是「明显不合法的请求」，
 * 真正的业务不变量由领域模型保证——两者不重复，而是各管一段。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
public class UserCreateCommand {

    @NotNull(message = "username is required")
    @Size(message = "username length must be between 4 and 20", min = 4, max = 20)
    private String username;

    @NotNull(message = "password is required")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$",
            message = "password must contain at least one letter and one number, and at least 8 characters")
    private String password;

    @NotNull(message = "gender is required")
    private Integer gender;

    @Pattern(regexp = "^(\\w)+(\\.\\w+)*@(\\w)+((\\.\\w+)+)$", message = "email must be a valid email address")
    private String email;

    @NotNull(message = "phoneNumber is required")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "phoneNumber must be a valid phone number")
    private String phoneNumber;
}
