package com.example.user.application.command;

/**
 * 注册用户用例的入参。
 */
public record RegisterUserCommand(

        String username,

        String password,

        Integer gender,

        String phoneNumber,

        String email
) {
}
