package com.ddk.event.starter.consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

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

    /**
     * 消费方在消费组里的稳定标识。去重表以「消费组:标识」记录处理过的事件，所以它决定了「同一个事件对这个消费方是否已经处理过」。
     * <p>
     * 默认是实现类的简单类名，取自用户类而不是 Spring 生成的代理类。它在这些情况下不够用，需要覆盖成一个固定的字符串：
     * <ul>
     *     <li>同一个消费组里有两个简单类名相同的去重消费方，或者同一个类注册了两个 Bean：启动会失败，提示补上标识；</li>
     *     <li>匿名类：没有类名，启动会失败；</li>
     *     <li>准备重命名实现类：标识随类名变化后，已经处理过的事件再被投递时会被当成新事件。先把标识固定成旧的类名再改名。</li>
     * </ul>
     */
    default String consumerId() {
        return ClassUtils.getUserClass(getClass()).getSimpleName();
    }

    /**
     * 这个消费方接受的事件类型和版本。默认 {@code null}：不检查，主题和标签匹配的消息都交给它，与发布方是否并行发布多个版本无关。
     * <p>
     * 发布方按约定在破坏性变更时发布新版本。声明契约后，类型不同的消息不会交给这个消费方，版本不认识的消息不会被解析成
     * {@link #payloadType()}，见 {@link EventContract}。
     */
    default @Nullable EventContract contract() {
        return null;
    }

    /**
     * 是否在事件所属的租户里处理。为 true 时，DDK 读取消息头 {@code ddk-event-tenant}（发布方用 {@code @IntegrationEvent(tenant = ...)}
     * 声明），以系统操作者 {@code ddk-event} 加这个租户的身份执行 {@link #handle} 和去重登记，结束后恢复线程原来的上下文。
     * 消息没有这个头时处理失败，由消息中间件重投，不会在没有租户的情况下执行。
     * <p>
     * 默认 false：不读取消息里的租户，{@code handle} 在没有操作者的上下文里执行。消息里的租户是发布方的声明，
     * 只在消费方确认信任这个来源时才据此切换身份。
     */
    default boolean runInEventTenant() {
        return false;
    }
}
