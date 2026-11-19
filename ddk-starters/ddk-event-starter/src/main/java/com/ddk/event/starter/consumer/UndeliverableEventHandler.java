package com.ddk.event.starter.consumer;

/**
 * 处理一条怎么也消费不了的消息：应用声明为 Bean，并设置 {@code ddk.event.rocketmq.consumer.max-attempts} 后生效。
 * <p>
 * 一条消息在一个消费组里失败到第 max-attempts 次时，DDK 把它交给这里。正常返回表示应用已经接手，例如存进自己的表留待处理，
 * 这条消息就算消费完成，顺序消费的队列继续往下走；抛出异常则照常失败、照常重试，下次失败时再交给这里。
 * <p>
 * DDK 不提供丢弃消息的默认实现：设置了上限却没有这个 Bean 时启动失败。
 */
@FunctionalInterface
public interface UndeliverableEventHandler {

    void handle(UndeliverableEvent event);
}
