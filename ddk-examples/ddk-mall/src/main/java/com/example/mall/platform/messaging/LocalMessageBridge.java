package com.example.mall.platform.messaging;

import com.ddk.core.domain.IntegrationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 没有消息中间件时的进程内转发，默认 profile 使用。
 * <p>
 * 事务提交后把消息序列化成 JSON，再交给 {@link MessageDispatcher}，消费方走的是和 RocketMQ 完全相同的路径：
 * 同样要自己解析消息体，同样看不到发布方的类型。它只用于本地开发：消息在内存里，进程退出就丢了，失败也不会重投。
 * <p>
 * 转发用单线程执行，消息按提交顺序处理。多线程的话，同一个订单的「已下单」和「已取消」可能颠倒，库存会先释放再预占。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mall.messaging.transport", havingValue = "local", matchIfMissing = true)
public class LocalMessageBridge {

    static final String EXECUTOR = "mallLocalMessageExecutor";

    @Bean(name = EXECUTOR, destroyMethod = "shutdown")
    Executor mallLocalMessageExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mall-local-messages");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    Forwarder mallLocalMessageForwarder(MessageDispatcher dispatcher, JsonMapper jsonMapper) {
        return new Forwarder(dispatcher, jsonMapper);
    }

    /**
     * 监听所有上下文发出的消息。
     */
    @Slf4j
    public static class Forwarder {

        private final MessageDispatcher dispatcher;

        private final JsonMapper jsonMapper;

        Forwarder(MessageDispatcher dispatcher, JsonMapper jsonMapper) {
            this.dispatcher = dispatcher;
            this.jsonMapper = jsonMapper;
        }

        @Async(EXECUTOR)
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(IntegrationMessage message) {
            IntegrationEvent contract = message.getClass().getAnnotation(IntegrationEvent.class);
            if (contract == null) {
                return;
            }
            String[] target = contract.value().split(":", 2);
            String json = jsonMapper.writeValueAsString(message);
            dispatcher.handlers().stream().map(MessageHandler::consumerGroup).distinct().forEach(group -> {
                try {
                    dispatcher.dispatch(group, target[0], target.length > 1 ? target[1] : "", message.eventId(), json);
                } catch (RuntimeException e) {
                    log.error("Local delivery of {} [{}] to {} failed and will not be retried", contract.value(), message.eventId(), group, e);
                }
            });
        }
    }
}
