package com.ddk.event.starter.consumer;

/**
 * 一类集成事件的消费方，声明为 Bean 即生效。
 * <p>
 * 消费方和控制器一样是用例的入口，放在适配层，只调用应用服务。消息体类型由消费方自己定义：它是发布方契约在消费方这边的一份拷贝，
 * 只声明用得到的字段，不引用发布方的类型。
 * <p>
 * 投递是至少一次：{@link #handle} 抛出异常时消息会被重新投递，所以处理逻辑必须可以重复执行，或者让 {@link #idempotent()} 返回 true。
 *
 * @param <T> 消息体类型
 */
public interface IntegrationEventConsumer<T> {

    /**
     * 消费组。同一个组里的多个实例分摊消息，不同的组各自收到全部消息。
     */
    String group();

    /**
     * 订阅的来源，写法与发布方的 {@code @IntegrationEvent(value)} 相同：{@code topic} 或 {@code topic:tag}。只写主题时接收该主题下的全部事件。
     */
    String source();

    Class<T> payloadType();

    void handle(ReceivedEvent<T> event);

    /**
     * 是否按事件标识去重。为 true 时 DDK 用 {@code IdempotentConsumer} 包住 {@link #handle}：去重登记和处理逻辑在同一个事务里，
     * 同一个事件只处理一次。需要开启 {@code ddk.event.inbox.enabled}，并且发布方声明了事件标识。
     * <p>
     * 处理逻辑要先加锁再开事务时不要用它：这里的事务会在进入 {@link #handle} 之前开启，锁就落在了事务里面。
     * 那种情况在应用服务里自己调用 {@code IdempotentConsumer}，把它放在锁和事务的里面。
     */
    default boolean idempotent() {
        return false;
    }
}
