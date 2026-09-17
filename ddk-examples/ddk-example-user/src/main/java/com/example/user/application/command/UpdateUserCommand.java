package com.example.user.application.command;

/**
 * 修改用户资料用例的入参，字段为 null 表示该项不修改。
 */
public record UpdateUserCommand(

        String username,

        String email
) {
}
