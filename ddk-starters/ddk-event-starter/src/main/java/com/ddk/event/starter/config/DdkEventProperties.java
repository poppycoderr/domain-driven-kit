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
     * 定时维护发布记录和已处理消息表。
     */
    private Maintenance maintenance = new Maintenance();

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
    public static class Maintenance {

        /**
         * 是否定时维护：重投失败的发布、删除旧的已完成发布、清理旧的已处理消息。默认关闭。
         * 有 ShedLock 的 {@code LockProvider}（例如引入了 ddk-job-starter）时同一时刻只有一个实例在维护；没有时每个实例都会做，
         * 失败的发布可能被几个实例同时重投。
         */
        private boolean enabled = false;

        /**
         * 两轮维护之间的间隔。
         */
        private Duration interval = Duration.ofMinutes(1);

        /**
         * 是否重投失败的发布（Spring Modulith 标记为 FAILED 的，包括陈旧检测判定卡住的）。
         */
        private boolean resubmitFailed = true;

        /**
         * 只重投失败了至少这么久的发布，给正在进行的重试留出时间。
         */
        private Duration resubmitMinAge = Duration.ofMinutes(1);

        /**
         * 每轮最多重投多少条。
         */
        private int resubmitBatchSize = 100;

        /**
         * 已完成的发布保留多久，之后删除；为零时不删除。只在 {@code spring.modulith.events.completion-mode=update}（默认）时有记录可删。
         */
        private Duration deleteCompletedAfter = Duration.ofDays(7);

        /**
         * 已处理消息表的记录保留多久，之后删除；为零时不删除。要长于一条消息可能被再次投递的最长时间，包括这里的重投，
         * 否则重投的消息会被当成新消息再处理一次。
         */
        private Duration inboxRetention = Duration.ofDays(7);

        /**
         * @throws IllegalStateException 间隔或最小年龄不为正、批量小于 1、保留期为负
         */
        public void validate() {
            if (!interval.isPositive() || !resubmitMinAge.isPositive() || resubmitBatchSize < 1
                    || deleteCompletedAfter.isNegative() || inboxRetention.isNegative()) {
                throw new IllegalStateException("Invalid ddk.event.maintenance settings: interval and resubmit-min-age must be positive, "
                        + "resubmit-batch-size at least 1, and the retention periods zero or positive");
            }
        }
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
