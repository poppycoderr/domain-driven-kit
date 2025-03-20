package com.ddk.adapter.controller;

import com.ddk.application.command.UserCreateCommand;
import com.ddk.application.command.UserUpdateCommand;
import com.ddk.application.query.UserPageQuery;
import com.ddk.application.response.UserDTO;
import com.ddk.application.service.UserService;
import com.ddk.core.page.PageResponse;
import com.ddk.core.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<Void> create(@Valid @RequestBody List<UserCreateCommand> commands) {
        userService.create(commands);
        return ApiResponse.ofSuccess();
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getById(@PathVariable Long id) {
        return ApiResponse.ofSuccess(userService.getById(id));
    }

    @PostMapping
    public ApiResponse<PageResponse<UserDTO>> getByPage(@Valid @RequestBody UserPageQuery query) {
        return ApiResponse.ofSuccess(userService.getByPage(query));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDTO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateCommand command) {
        userService.update(command);
        return ApiResponse.ofSuccess();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteById(@PathVariable Long id) {
        userService.deleteById(id);
        return ApiResponse.ofSuccess();
    }
}
