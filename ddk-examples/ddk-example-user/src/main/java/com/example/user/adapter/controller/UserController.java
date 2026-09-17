package com.example.user.adapter.controller;

import com.ddk.core.page.PageResponse;
import com.ddk.core.response.ApiResponse;
import com.example.user.adapter.controller.request.RegisterUserRequest;
import com.example.user.adapter.controller.request.UpdateUserRequest;
import com.example.user.application.query.UserPageQuery;
import com.example.user.application.response.UserResponse;
import com.example.user.application.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。适配层只负责协议转换：校验请求、转成命令交给应用服务、包装响应，不写业务判断。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ApiResponse.ofSuccess(userService.register(request.toCommand()));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable("id") Long id) {
        return ApiResponse.ofSuccess(userService.get(id));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<UserResponse>> page(@Valid @RequestBody UserPageQuery query) {
        return ApiResponse.ofSuccess(userService.page(query));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ofSuccess(userService.update(id, request.toCommand()));
    }

    @PatchMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable("id") Long id, @RequestParam(value = "reason", required = false) String reason) {
        userService.disable(id, reason);
        return ApiResponse.ofSuccess();
    }

    @PatchMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable("id") Long id) {
        userService.enable(id);
        return ApiResponse.ofSuccess();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return ApiResponse.ofSuccess();
    }
}
