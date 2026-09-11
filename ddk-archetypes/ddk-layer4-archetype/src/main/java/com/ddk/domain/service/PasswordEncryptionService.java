package com.ddk.domain.service;

/**
 * 密码加密领域服务。
 * <p>
 * 为什么是领域服务而不是聚合根上的方法：加密策略是跨聚合的领域知识，
 * 也不属于任何一个用户实例的状态。领域服务就是这种「无处安放的领域行为」的位置。
 * <p>
 * 它是接口而不是实现：领域层定义契约，具体算法（BCrypt / Argon2 / 公司统一的 KMS）
 * 由基础设施层提供，领域层不依赖任何加密库。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
public interface PasswordEncryptionService {

    String encrypt(String rawPassword);

    boolean matches(String rawPassword, String encryptedPassword);
}
