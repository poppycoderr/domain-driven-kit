package com.ddk.projection.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 读模型投影配置。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkProjectionProperties.PREFIX)
public class DdkProjectionProperties {

    public static final String PREFIX = "ddk.projection";

    /**
     * 是否启用。关闭后不注册 {@code Projections}，也不处理待刷新的标记。
     */
    private boolean enabled = true;

    /**
     * 待刷新表的表名。
     */
    private String table = "ddk_projection_pending";

    /**
     * 启动时是否自动建表。用迁移工具管理表结构时关掉，把建表语句放进迁移脚本。
     */
    private boolean initializeSchema = true;

    /**
     * 清扫的间隔。事务提交后会立即刷新，清扫处理的是遗留的标记：别的实例留下的、重启前没处理完的、到了重试时间的。
     */
    private Duration sweepInterval = Duration.ofSeconds(30);

    /**
     * 每次从待刷新表里取多少条。
     */
    private int batchSize = 100;

    /**
     * 刷新失败后第一次重试前等待的时间，之后每失败一次翻倍。
     */
    private Duration retryDelay = Duration.ofSeconds(5);

    /**
     * 重试等待时间的上限。
     */
    private Duration maxRetryDelay = Duration.ofMinutes(5);

    /**
     * 领取一条标记后独占它的时间。超过这个时间还没处理完（停顿、宕机），别的实例可以接手。
     * 应当长于一次刷新的正常耗时；短了不会出错，只会多刷新几次。
     */
    private Duration leaseTime = Duration.ofMinutes(2);

    /**
     * 启动时检查配置，不合法的配置会让后台线程空转或不停重试，要在启动时就暴露。
     *
     * @throws IllegalStateException 配置不合法
     */
    public void validate() {
        require(batchSize > 0, "ddk.projection.batch-size must be greater than 0, was " + batchSize);
        requirePositive(sweepInterval, "sweep-interval");
        requirePositive(retryDelay, "retry-delay");
        requirePositive(maxRetryDelay, "max-retry-delay");
        requirePositive(leaseTime, "lease-time");
        require(retryDelay.compareTo(maxRetryDelay) <= 0,
                "ddk.projection.retry-delay (" + retryDelay + ") must not exceed ddk.projection.max-retry-delay (" + maxRetryDelay + ")");
    }

    private static void requirePositive(Duration value, String name) {
        require(value != null && value.isPositive(), "ddk.projection." + name + " must be a positive duration, was " + value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
