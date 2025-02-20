package io.github.ddk.domain.model.entity;

import io.github.ddk.domain.model.enums.Gender;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Getter
@ToString
public class User {

    private Long id;

    @NonNull
    private String username;

    @NonNull
    private Gender gender;

    @NonNull
    private String password;

    @NonNull
    private String phoneNumber;

    private String email;

    @NonNull
    private Boolean status;
}