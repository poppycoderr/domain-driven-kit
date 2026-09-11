package com.ddk.infrastructure.security;

import com.ddk.domain.service.PasswordEncryptionService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 领域服务的基础设施实现。
 * <p>
 * <b>示例用途，不要用于生产。</b>SHA-256 没有加盐、没有慢哈希因子，
 * 抗不住彩虹表和离线爆破。真实项目请换成 BCrypt / Argon2（例如
 * {@code spring-security-crypto} 的 {@code BCryptPasswordEncoder}）。
 * 这里保持零额外依赖，只为演示「领域层定契约、基础设施层给实现」的分工。
 *
 * @author Elijah Du
 */
@Component
public class Sha256PasswordEncryptionService implements PasswordEncryptionService {

    @Override
    public String encrypt(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    @Override
    public boolean matches(String rawPassword, String encryptedPassword) {
        return MessageDigest.isEqual(
                encrypt(rawPassword).getBytes(StandardCharsets.UTF_8),
                encryptedPassword.getBytes(StandardCharsets.UTF_8));
    }
}
