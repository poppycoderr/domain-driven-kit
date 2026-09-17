package com.example.user.infrastructure.security;

import com.example.user.domain.service.PasswordEncryptionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 基于 BCrypt 的密码加密实现：自带随机盐与可调的计算强度。
 */
@Component
public class BCryptPasswordEncryptionService implements PasswordEncryptionService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String encrypt(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encryptedPassword) {
        return encoder.matches(rawPassword, encryptedPassword);
    }
}
