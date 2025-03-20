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
public class UserUpdateCommand {

    @NotNull(message = "username is required")
    @Size(message = "username length must be between 4 and 20", min = 4, max = 20)
    private String username;

    @Pattern(regexp = "^(\\w)+(\\.\\w+)*@(\\w)+((\\.\\w+)+)$", message = "email must be a valid email address")
    private String email;
}
