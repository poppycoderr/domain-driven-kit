package com.ddk.application.command;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
public class UserCreateCommand {

    @NotNull(message = "username is required")
    @Size(message = "username length must be between 4 and 20", min = 4, max = 20)
    private String username;

    @NotNull(message = "password is required")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$", message = "password must contain at least one letter and one number, and at least 8 characters")
    private String password;

    @Pattern(regexp = "^(\\w)+(\\.\\w+)*@(\\w)+((\\.\\w+)+)$", message = "email must be a valid email address")
    private String email;

    @NotNull(message = "phoneNumber is required")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "phoneNumber must be a valid phone number")
    private String phoneNumber;
}
