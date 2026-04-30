package com.ddk.seata.starter.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Seata 自动配置
 * <p>
 * 依赖 seata-spring-boot-starter 的原生自动配置，
 * 此类可用于扩展 DDK 特定的 Seata 配置。
 *
 * @author Elijah Du
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "seata", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DdkSeataAutoConfiguration {

    // Seata's own auto-configuration handles most setup.
    // DDK-specific Seata beans or customizations can be added here.
}
