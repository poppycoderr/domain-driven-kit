package com.ddk.event.starter.config;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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

    /**
     * 消费端幂等（已处理消息表）。
     */
    private Inbox inbox = new Inbox();

    /**
     * 经 RocketMQ 投递集成事件。
     */
    private RocketMq rocketmq = new RocketMq();

    @Data
    public static class Inbox {

        /**
         * 是否注册 {@code IdempotentConsumer}。需要 {@code JdbcTemplate} 与事务管理器，默认关闭。
         */
        private boolean enabled = false;

        /**
         * 已处理消息表名。
         */
        private String table = "ddk_processed_message";

        /**
         * 启动时是否执行 {@code CREATE TABLE IF NOT EXISTS}。由迁移工具管理表结构时关闭。
         */
        private boolean initializeSchema = true;
    }

    @Data
    public static class RocketMq {

        /**
         * NameServer 地址，多个用分号分隔。设置后 DDK 创建投递用的 {@code DefaultMQProducer}；应用已有 producer 时以应用为准。
         */
        private @Nullable String nameServer;

        /**
         * 投递用 producer 的生产者组。
         */
        private String producerGroup = "ddk-event-producer";

        /**
         * 单次发送超时。
         */
        private Duration sendTimeout = Duration.ofSeconds(3);
    }
}
