package com.example.user.adapter.mcp;

import com.example.user.application.command.RegisterUserCommand;
import com.example.user.application.response.UserResponse;
import com.example.user.application.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 用户用例的 MCP 工具。与 {@code UserController} 同属适配层：校验入参、转成命令交给应用服务，不写业务判断，也不接触仓储。
 */
@Component
@RequiredArgsConstructor
public class UserMcpTools {

    private final UserService userService;

    @McpTool(name = "register_user", description = "Register a new user. Usernames must be unique.")
    public UserResponse register(
            @McpToolParam(description = "Username, 4 to 20 characters") @NotBlank @Size(min = 4, max = 20) String username,
            @McpToolParam(description = "Password with letters and digits, 8 to 64 characters")
            @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "must contain letters and digits, 8 to 64 characters")
            String password,
            @McpToolParam(description = "Gender code: 0 male, 1 female") @NotNull Integer gender,
            @McpToolParam(description = "Mainland China mobile number") @NotBlank String phoneNumber,
            @McpToolParam(description = "Email address", required = false) @Nullable String email) {
        return userService.register(new RegisterUserCommand(username, password, gender, phoneNumber, email));
    }

    @McpTool(name = "get_user", description = "Get a user by ID. The phone number is masked.")
    public UserResponse get(@McpToolParam(description = "User ID") @Positive long id) {
        return userService.get(id);
    }

    @McpTool(name = "disable_user", description = "Disable a user so they can no longer sign in. Disabling twice has no effect.")
    public String disable(
            @McpToolParam(description = "User ID") @Positive long id,
            @McpToolParam(description = "Why the user is disabled", required = false) @Nullable String reason) {
        userService.disable(id, reason);
        return "User " + id + " disabled";
    }
}
