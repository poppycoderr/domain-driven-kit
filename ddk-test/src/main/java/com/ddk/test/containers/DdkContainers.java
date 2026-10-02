package com.ddk.test.containers;

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试常用中间件的容器预置，镜像版本固定，与 DDK 自己的测试使用的一致。
 *
 * <pre>{@code
 * @Testcontainers(disabledWithoutDocker = true)
 * class OrderRepositoryTest {
 *
 *     @Container
 *     static final RedisContainer REDIS = DdkContainers.redis();
 *
 *     @DynamicPropertySource
 *     static void redis(DynamicPropertyRegistry registry) {
 *         registry.add("spring.data.redis.host", REDIS::getHost);
 *         registry.add("spring.data.redis.port", REDIS::getPort);
 *     }
 * }
 * }</pre>
 */
public final class DdkContainers {

    public static final String REDIS_IMAGE = "redis:7-alpine";

    public static final String MYSQL_IMAGE = "mysql:8.4";

    public static final String ROCKETMQ_IMAGE = "apache/rocketmq:5.5.0";

    private DdkContainers() {
    }

    public static RedisContainer redis() {
        return new RedisContainer(DockerImageName.parse(REDIS_IMAGE));
    }

    /**
     * MySQL 8.4，字符集 {@code utf8mb4}。需要测试类路径上有 {@code org.testcontainers:testcontainers-mysql} 和 MySQL 驱动。
     */
    public static MySQLContainer mysql() {
        return new MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
    }

    public static RocketMqContainer rocketmq() {
        return new RocketMqContainer(DockerImageName.parse(ROCKETMQ_IMAGE));
    }
}
