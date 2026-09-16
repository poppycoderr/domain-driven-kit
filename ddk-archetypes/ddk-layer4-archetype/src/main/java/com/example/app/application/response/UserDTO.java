package com.example.app.application.response;

import lombok.Data;

/**
 * 用户对外响应对象。
 * <p>
 * 刻意和领域实体分开：DTO 可以贫血、可以有 setter、可以被 Jackson 反射，
 * 领域实体不行。手机号这里是脱敏后的值。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
public class UserDTO {

    private Long id;

    private String username;

    private String gender;

    private String email;

    /** 脱敏后的手机号，如 138****8000 */
    private String phoneNumber;

    private Boolean status;
}
