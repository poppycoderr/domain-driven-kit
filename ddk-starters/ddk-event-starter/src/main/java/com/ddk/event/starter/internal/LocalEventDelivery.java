package com.ddk.event.starter.internal;

import com.ddk.core.domain.IntegrationEvent;
import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 没有消息中间件时，在进程内把集成事件送给本应用里的消费方。
 * <p>
 * 事务提交后把事件序列化成 JSON 再分发，消费方走的是和真实中间件完全相同的路径：同样要解析消息体，同样拿不到发布方的对象。
 * 用单线程按提交顺序处理，同一个聚合的事件不会颠倒。
 * <p>
 * 链路上下文在发布时写进消息头，处理时再取出来，和经过消息中间件时一样。
 * <p>
 * 只用于本地开发和测试：事件在内存里，进程退出就丢，处理失败只记日志，不会重投。
 */
public class LocalEventDelivery implements ApplicationListener<PayloadApplicationEvent<?>>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LocalEventDelivery.class);

    private final IntegrationEventDispatcher dispatcher;

    private final IntegrationEventRouting routing;

    private final JsonMapper jsonMapper;

    private final EventObservations observations;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ddk-local-events");
        thread.setDaemon(true);
        return thread;
    });

    public LocalEventDelivery(IntegrationEventDispatcher dispatcher, IntegrationEventRouting routing, JsonMapper jsonMapper) {
        this(dispatcher, routing, jsonMapper, EventObservations.NOOP);
    }

    public LocalEventDelivery(IntegrationEventDispatcher dispatcher, IntegrationEventRouting routing, JsonMapper jsonMapper,
            EventObservations observations) {
        this.observations = observations;
        this.dispatcher = dispatcher;
        this.routing = routing;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 这里用普通的监听器加事务同步，而不是 {@code @TransactionalEventListener}：后者要么声明具体的事件类型，要么监听 {@code Object}，
     * 而监听 {@code Object} 的事务监听器会让 Spring Modulith 把应用里的每一个事件都写进发布记录。
     * <p>
     * 事件在发布时就序列化，之后对象再被修改也不影响送出去的内容。没有事务时不送达，与真实投递的行为一致。
     */
    @Override
    public void onApplicationEvent(PayloadApplicationEvent<?> published) {
        Object event = published.getPayload();
        IntegrationEvent contract = event.getClass().getAnnotation(IntegrationEvent.class);
        if (contract == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("{} was published outside a transaction and is not delivered", event.getClass().getName());
            return;
        }
        String[] target = contract.value().split(":", 2);
        Map<String, String> headers = new LinkedHashMap<>();
        routing.headers(event).forEach((name, value) -> headers.put(name, String.valueOf(value)));
        String json = jsonMapper.writeValueAsString(event);
        observations.startPublish(target[0], headers::put).stop();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                executor.execute(() -> deliver(target[0], target.length > 1 ? target[1] : null, headers, json));
            }
        });
    }

    private void deliver(String topic, @Nullable String tag, Map<String, String> headers, String json) {
        dispatcher.consumers().stream().map(IntegrationEventConsumer::group).distinct().forEach(group -> {
            try {
                dispatcher.dispatch(group, topic, tag, headers, json);
            } catch (RuntimeException e) {
                log.error("Local delivery of {}{} to group {} failed and will not be retried", topic, tag == null ? "" : ":" + tag, group, e);
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
