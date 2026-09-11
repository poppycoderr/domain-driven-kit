package com.ddk.adapter.controller;

import com.ddk.application.command.UserCreateCommand;
import com.ddk.application.command.UserUpdateCommand;
import com.ddk.application.query.UserPageQuery;
import com.ddk.application.response.UserDTO;
import com.ddk.application.service.UserService;
import com.ddk.core.page.PageResponse;
import com.ddk.core.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户接口。
 * <p>
 * 适配层只做三件事：<b>接收协议、转交应用服务、包装响应</b>。
 * 这里不应出现任何 if 形式的业务判断，也不应直接碰领域对象——
 * 它拿到和返回的都是应用层的命令与 DTO。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserDTO> register(@Valid @RequestBody UserCreateCommand command) {
        return ApiResponse.ofSuccess(userService.register(command));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getById(@PathVariable Long id) {
        return ApiResponse.ofSuccess(userService.getById(id));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<UserDTO>> getByPage(@Valid @RequestBody UserPageQuery query) {
        return ApiResponse.ofSuccess(userService.getByPage(query));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDTO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateCommand command) {
        return ApiResponse.ofSuccess(userService.update(id, command));
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id, @RequestParam(required = false) String reason) {
        userService.disable(id, reason);
        return ApiResponse.ofSuccess();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteById(@PathVariable Long id) {
        userService.deleteById(id);
        return ApiResponse.ofSuccess();
    }
}
