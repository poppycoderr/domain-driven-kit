package com.example.mall.platform.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 把收到的消息按主题和标签分发给消费方，与消息从哪里来无关：RocketMQ 的监听器和进程内的转发都走这里。
 */
@Slf4j
@Component
public class MessageDispatcher {

    private final List<MessageHandler<?>> handlers;

    private final JsonMapper jsonMapper;

    public MessageDispatcher(List<MessageHandler<?>> handlers, JsonMapper jsonMapper) {
        this.handlers = handlers;
        this.jsonMapper = jsonMapper;
    }

    public List<MessageHandler<?>> handlers() {
        return handlers;
    }

    /**
     * 只分发给指定消费组里订阅了这个主题和标签的消费方。任何一个消费方失败都会抛出异常，由调用方决定重试。
     */
    public void dispatch(String consumerGroup, String topic, String tag, String messageId, String json) {
        for (MessageHandler<?> handler : handlers) {
            if (handler.consumerGroup().equals(consumerGroup) && handler.topic().equals(topic) && handler.tag().equals(tag)) {
                log.info("Message {}:{} [{}] -> {}", topic, tag, messageId, handler.getClass().getSimpleName());
                invoke(handler, messageId, json);
            }
        }
    }

    private <T> void invoke(MessageHandler<T> handler, String messageId, String json) {
        handler.handle(messageId, jsonMapper.readValue(json, handler.payloadType()));
    }
}
