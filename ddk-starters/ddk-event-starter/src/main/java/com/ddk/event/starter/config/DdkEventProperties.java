package com.ddk.event.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 领域事件配置。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkEventProperties.PREFIX)
public class DdkEventProperties {

    public static final String PREFIX = "ddk.event";

    /**
     * 是否启用领域事件发布。
     * <p>
     * 关掉之后 {@code DomainEventPublisher} 不再注册，通用仓储会跳过事件发布
     * （它把发布器当可选依赖），聚合上登记的事件只会被丢弃。
     * 适合「暂时不想让事件产生副作用」的排查场景。
     */
    private boolean enabled = true;
}
