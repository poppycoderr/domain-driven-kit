package com.example.mall.platform.messaging;

/**
 * 一类消息的消费方。实现放在各上下文的 {@code adapter.messaging} 包里：消息监听和控制器一样是用例的入口，只调用应用服务。
 *
 * @param <T> 消费方自己定义的消息体类型。它是发布方契约在消费方这边的一份拷贝，只声明用得到的字段
 */
public interface MessageHandler<T> {

    /**
     * 消费组，同一个上下文的消费方用同一个。
     */
    String consumerGroup();

    String topic();

    String tag();

    Class<T> payloadType();

    /**
     * 处理失败时抛出异常，消息会被重新投递，所以处理逻辑必须可以重复执行。
     */
    void handle(String messageId, T payload);
}
