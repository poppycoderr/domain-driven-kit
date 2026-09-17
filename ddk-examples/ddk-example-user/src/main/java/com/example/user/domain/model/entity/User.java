package com.example.user.domain.model.entity;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.example.user.domain.error.UserError;
import com.example.user.domain.event.UserDisabledEvent;
import com.example.user.domain.event.UserRegisteredEvent;
import com.example.user.domain.model.enums.Gender;
import com.example.user.domain.model.valueobject.Email;
import com.example.user.domain.model.valueobject.PhoneNumber;

import java.util.Objects;

/**
 * 用户聚合根。没有公开构造器和 setter，创建、重建与所有状态变更都经由领域方法，不变量只在这里守卫。
 */
public class User extends AggregateRoot<UserId> {

    private String username;
    private String encryptedPassword;
    private Gender gender;
    private PhoneNumber phoneNumber;
    private Email email;
    private boolean enabled;

    private User() {
    }

    /**
     * 注册新用户并登记 {@link UserRegisteredEvent}。标识由调用方预先生成，事件从诞生起就能带上用户 ID。
     */
    public static User register(UserId id, String username, String encryptedPassword, Gender gender, PhoneNumber phoneNumber, Email email) {
        User user = new User();
        user.assignId(Objects.requireNonNull(id, "id"));
        user.username = requireValidUsername(username);
        user.encryptedPassword = Objects.requireNonNull(encryptedPassword, "encryptedPassword");
        user.gender = Objects.requireNonNull(gender, "gender");
        user.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber");
        user.email = email;
        user.enabled = true;
        user.registerEvent(new UserRegisteredEvent(id, username));
        return user;
    }

    /**
     * 从持久化数据重建。与 {@link #register} 不同，重建不产生领域事件：读出一个用户并不意味着它刚刚注册。
     */
    public static User restore(UserId id, String username, String encryptedPassword, Gender gender, PhoneNumber phoneNumber, Email email,
                               boolean enabled, Long version) {
        User user = new User();
        user.assignId(Objects.requireNonNull(id, "id"));
        user.username = username;
        user.encryptedPassword = encryptedPassword;
        user.gender = gender;
        user.phoneNumber = phoneNumber;
        user.email = email;
        user.enabled = enabled;
        user.assignVersion(version);
        return user;
    }

    /**
     * 修改资料，参数为 null 表示该项不修改。
     */
    public void changeProfile(String newUsername, Email newEmail) {
        if (newUsername != null) {
            this.username = requireValidUsername(newUsername);
        }
        if (newEmail != null) {
            this.email = newEmail;
        }
    }

    public boolean isRenamingTo(String newUsername) {
        return newUsername != null && !newUsername.equals(username);
    }

    /**
     * 禁用用户。幂等：已禁用时不报错，也不重复登记事件。
     */
    public void disable(String reason) {
        if (!enabled) {
            return;
        }
        this.enabled = false;
        registerEvent(new UserDisabledEvent(id(), reason));
    }

    public void enable() {
        this.enabled = true;
    }

    private static String requireValidUsername(String username) {
        if (username == null || username.length() < 4 || username.length() > 20) {
            throw new BusinessException(UserError.INVALID_USERNAME, username);
        }
        return username;
    }

    public String username() {
        return username;
    }

    public String encryptedPassword() {
        return encryptedPassword;
    }

    public Gender gender() {
        return gender;
    }

    public PhoneNumber phoneNumber() {
        return phoneNumber;
    }

    public Email email() {
        return email;
    }

    public boolean enabled() {
        return enabled;
    }
}
