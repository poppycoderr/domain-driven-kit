package com.ddk.test.containers;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 在一个容器里运行 RocketMQ 的 NameServer 与一个 broker。
 * <p>
 * RocketMQ 的客户端先问 NameServer 要 broker 的地址，再按这个地址直连 broker。所以 broker 登记的地址必须是宿主机能访问的：
 * 这里在宿主机上选一个空闲端口，让 broker 在容器里也监听这个端口，并把它原样映射出来。自动建 topic 是关闭的，
 * 测试用 {@link #createTopic(String, int)} 显式创建，行为才是确定的。
 */
public class RocketMqContainer extends GenericContainer<RocketMqContainer> {

    private static final int NAME_SERVER_PORT = 9876;

    private final int brokerPort = freePort();

    public RocketMqContainer(DockerImageName image) {
        super(image);
        String host = DockerClientFactory.instance().dockerHostIpAddress();
        String brokerIp = "localhost".equals(host) ? "127.0.0.1" : host;
        withExposedPorts(NAME_SERVER_PORT);
        withEnv("JAVA_OPT_EXT", "-Xms256m -Xmx256m -Xmn128m");
        // Linux 上的 Docker 只为容器声明过的端口做映射，所以 broker 端口要同时声明并绑定
        withCreateContainerCmdModifier(cmd -> {
            List<ExposedPort> exposed = new ArrayList<>(Arrays.asList(cmd.getExposedPorts()));
            exposed.add(ExposedPort.tcp(brokerPort));
            cmd.withExposedPorts(exposed);
            HostConfig hostConfig = Objects.requireNonNull(cmd.getHostConfig(), "host config");
            Ports ports = hostConfig.getPortBindings() == null ? new Ports() : hostConfig.getPortBindings();
            ports.bind(ExposedPort.tcp(brokerPort), Ports.Binding.bindPort(brokerPort));
            hostConfig.withPortBindings(ports);
        });
        withCommand("sh", "-c", String.join(" && ",
                "printf 'brokerClusterName=DefaultCluster\\nbrokerName=broker-a\\nbrokerId=0\\nbrokerIP1=" + brokerIp + "\\nlistenPort="
                        + brokerPort + "\\nautoCreateTopicEnable=false\\n' > /tmp/broker.conf",
                "(./mqnamesrv &)",
                "./mqbroker -n localhost:" + NAME_SERVER_PORT + " -c /tmp/broker.conf"));
        waitingFor(Wait.forLogMessage(".*The broker.*boot success.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
    }

    /**
     * NameServer 地址，用于 producer 与 consumer 的 {@code namesrvAddr}。
     */
    public String getNameServer() {
        return getHost() + ":" + getMappedPort(NAME_SERVER_PORT);
    }

    /**
     * 创建 topic，并等到它的路由在 NameServer 上可见后返回。
     * <p>
     * 直接对 broker 创建：按集群创建要先等 broker 登记到 NameServer，否则 {@code mqadmin} 什么也不做却照样返回 0。
     * broker 把路由登记到 NameServer 是异步的，不等的话紧接着的发送会得到「No route info」。
     */
    public void createTopic(String topic, int queues) {
        String created = exec("./mqadmin", "updateTopic", "-b", "127.0.0.1:" + brokerPort, "-t", topic,
                "-r", String.valueOf(queues), "-w", String.valueOf(queues));
        if (!created.contains("success")) {
            throw new IllegalStateException("Failed to create RocketMQ topic " + topic + ": " + created);
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!exec("./mqadmin", "topicRoute", "-n", "localhost:" + NAME_SERVER_PORT, "-t", topic).contains("brokerDatas")) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Route of RocketMQ topic " + topic + " did not become visible in time");
            }
            sleep();
        }
    }

    private String exec(String... command) {
        try {
            ExecResult result = execInContainer(command);
            return result.getStdout() + result.getStderr();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + String.join(" ", command), e);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the topic route", e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
