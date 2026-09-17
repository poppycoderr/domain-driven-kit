package com.example.user.domain.service;

/**
 * 密码加密领域服务。领域层定义契约，具体算法由基础设施层提供，领域层不依赖加密库。
 */
public interface PasswordEncryptionService {

    String encrypt(String rawPassword);

    boolean matches(String rawPassword, String encryptedPassword);
}
