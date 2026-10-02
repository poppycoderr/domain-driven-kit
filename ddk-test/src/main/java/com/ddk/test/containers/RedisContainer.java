package com.ddk.test.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 单机 Redis。
 */
public class RedisContainer extends GenericContainer<RedisContainer> {

    private static final int REDIS_PORT = 6379;

    public RedisContainer(DockerImageName image) {
        super(image);
        withExposedPorts(REDIS_PORT);
    }

    /**
     * 宿主机上映射到 Redis 的端口，配合 {@link #getHost()} 使用。
     */
    public int getPort() {
        return getMappedPort(REDIS_PORT);
    }
}
