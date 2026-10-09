package com.ddk.job.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 定时任务配置。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkJobProperties.PREFIX)
public class DdkJobProperties {

    public static final String PREFIX = "ddk.job";

    /**
     * 是否启用。关闭后不开启调度，任务不会执行，适合只想跑接口的本地环境。
     */
    private boolean enabled = true;

    private Lock lock = new Lock();

    public enum Store {
        REDIS,
        LOCAL
    }

    @Data
    public static class Lock {

        /**
         * 锁放在哪里。{@code redis} 让多个实例互斥；{@code local} 把锁放在当前进程的内存里，不需要 Redis，但只能用在单实例上，
         * 适合本地开发。
         */
        private Store store = Store.REDIS;

        /**
         * 持有锁的默认最长时间。执行任务的实例宕机后，其他实例最多等这么久才能接手，所以要比任务的正常耗时长，但不要长太多。
         */
        private Duration atMostFor = Duration.ofMinutes(10);

        /**
         * 持有锁的默认最短时间。任务很快结束时锁仍保留这么久，防止各实例时钟有偏差时同一轮被执行两次。
         */
        private Duration atLeastFor = Duration.ZERO;

        /**
         * Redis 里锁 key 的前缀，完整的 key 是「前缀:应用名:任务名」。
         */
        private String keyPrefix = "ddk:job-lock";
    }
}
