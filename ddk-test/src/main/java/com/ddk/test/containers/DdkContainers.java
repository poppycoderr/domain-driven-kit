package com.ddk.test.containers;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
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

    public static final String ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.4.5";

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

    /**
     * 单节点的 Elasticsearch 9，关闭了安全认证，用 {@code getHttpHostAddress()} 取访问地址。
     * 需要测试类路径上有 {@code org.testcontainers:testcontainers-elasticsearch}。镜像约 1.1 GB，容器占用约 1 GB 内存。
     */
    public static ElasticsearchContainer elasticsearch() {
        return new ElasticsearchContainer(DockerImageName.parse(ELASTICSEARCH_IMAGE))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }

    public static RocketMqContainer rocketmq() {
        return new RocketMqContainer(DockerImageName.parse(ROCKETMQ_IMAGE));
    }
}
