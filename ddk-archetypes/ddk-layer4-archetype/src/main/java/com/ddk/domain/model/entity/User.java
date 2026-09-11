package com.ddk.domain.model.entity;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.domain.event.UserDisabledEvent;
import com.ddk.domain.event.UserRegisteredEvent;
import com.ddk.domain.model.enums.Gender;
import com.ddk.domain.model.valueobject.Email;
import com.ddk.domain.model.valueobject.PhoneNumber;

import java.util.Objects;

/**
 * 用户聚合根。
 * <p>
 * 这个类是 DDK 领域模型的示例，重点看三件事：
 * <ol>
 *     <li><b>没有公开构造器和 setter</b>——所有状态变更都通过领域方法，不变量才有唯一守卫位置</li>
 *     <li><b>创建走工厂方法</b> {@link #register}——创建也是领域行为，也要校验</li>
 *     <li><b>状态机约束写在实体里</b>，而不是散落在 Service 的 if 里</li>
 * </ol>
 * 对比一下：如果这里只有字段和 {@code @Data}，它就只是个换了包名的 DTO。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
public class User extends AggregateRoot<UserId> {

    private String username;
    private String encryptedPassword;
    private Gender gender;
    private PhoneNumber phoneNumber;
    private Email email;
    private boolean enabled;

    /** 私有构造器：外部只能通过工厂方法或重建方法拿到实例 */
    private User() {
    }

    /**
     * 注册一个新用户。
     * <p>
     * 此时还没有 ID（数据库自增），{@code isNew()} 为 true，由仓储保存后调用
     * {@link #onPersisted} 回填。注册事件也在那时才登记——事件要带上用户 ID，
     * 而这里 ID 还不存在。这是自增主键方案必须付出的代价，
     * 想在工厂方法里就发事件，得改用 UUID / 雪花提前生成标识。
     *
     * @param encryptedPassword 已加密的密码，加密由领域服务完成，聚合根不碰明文
     */
    public static User register(String username, String encryptedPassword, Gender gender,
                                PhoneNumber phoneNumber, Email email) {
        User user = new User();
        user.username = requireUsername(username);
        user.encryptedPassword = Objects.requireNonNull(encryptedPassword, "密码不能为空");
        user.gender = Objects.requireNonNull(gender, "性别不能为空");
        user.phoneNumber = Objects.requireNonNull(phoneNumber, "手机号不能为空");
        user.email = email;
        user.enabled = true;
        return user;
    }

    /**
     * 从持久化数据重建聚合。
     * <p>
     * 和 {@link #register} 的区别：重建不产生领域事件——从数据库读出一个用户，
     * 并不意味着「用户刚刚注册」。这是最容易写错的地方之一。
     */
    public static User restore(UserId id, String username, String encryptedPassword, Gender gender,
                               PhoneNumber phoneNumber, Email email, boolean enabled, Long version) {
        User user = new User();
        user.id = Objects.requireNonNull(id, "重建聚合必须有标识");
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
     * 由仓储在插入成功后回填数据库自增主键，并在此时登记注册事件。
     */
    public void onPersisted(UserId id) {
        assignId(id);
        registerEvent(new UserRegisteredEvent(id, username));
    }

    public void rename(String newUsername) {
        this.username = requireUsername(newUsername);
    }

    public void changeEmail(Email newEmail) {
        this.email = newEmail;
    }

    /**
     * 禁用用户。
     * <p>
     * 幂等：已禁用的用户重复调用不报错，也不重复发事件。
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

    private static String requireUsername(String username) {
        if (username == null || username.length() < 4 || username.length() > 20) {
            throw new IllegalArgumentException("用户名长度必须在 4 到 20 之间，实际为 " + username);
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
