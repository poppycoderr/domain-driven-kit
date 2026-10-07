package com.example.mall.platform;

import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * 没有 Redis 时（默认 profile）定时任务用的锁：每次都放行。
 * <p>
 * DDK 的定时任务 starter 把任务锁放在 Redis 里。默认 profile 不连 Redis，这里提供一个总是成功的 {@link LockProvider}，
 * 应用声明了自己的锁实现时 starter 就不再创建 Redis 的那个。它只适合单实例。条件和库存锁一致：{@code ddk.concurrency.enabled=false}
 * 在这个应用里表示「当前环境没有 Redis」。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ddk.concurrency.enabled", havingValue = "false")
public class LocalJobLock {

    @Bean
    LockProvider localJobLockProvider() {
        return configuration -> Optional.of(() -> {
        });
    }
}
