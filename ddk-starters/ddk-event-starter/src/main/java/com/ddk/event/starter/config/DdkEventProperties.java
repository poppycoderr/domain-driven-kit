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

    /**
     * 进程内转发集成事件。
     */
    private LocalDelivery localDelivery = new LocalDelivery();

    /**
     * 集成事件在事务之外发布时怎么办。这样的事件不会被投递：{@code fail} 在发布处抛出异常，{@code warn} 只记一条警告，
     * {@code ignore} 保持沉默。
     */
    private OutsideTransaction outsideTransaction = OutsideTransaction.FAIL;

    public enum OutsideTransaction {
        FAIL,
        WARN,
        IGNORE
    }

    @Data
    public static class LocalDelivery {

        /**
         * 是否在进程内把集成事件送给本应用里的消费方。只用于本地开发和测试：事件不持久，失败不重投。不要与真实的消息中间件同时开启。
         */
        private boolean enabled = false;
    }

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

        private Consumer consumer = new Consumer();

        @Data
        public static class Consumer {

            /**
             * 应用声明了消费方时，是否从 RocketMQ 消费。
             */
            private boolean enabled = true;

            /**
             * 是否顺序消费。顺序消费按队列保持事件的先后，一条消息处理失败会暂停它所在的队列；关闭后并发消费，失败的消息单独重投。
             */
            private boolean orderly = true;
        }
    }
}
