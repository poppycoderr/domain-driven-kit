package com.example.app.application.command;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户更新命令。字段为 null 表示「本次不修改」。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
public class UserUpdateCommand {

    @Size(message = "username length must be between 4 and 20", min = 4, max = 20)
    private String username;

    @Pattern(regexp = "^(\\w)+(\\.\\w+)*@(\\w)+((\\.\\w+)+)$", message = "email must be a valid email address")
    private String email;
}
