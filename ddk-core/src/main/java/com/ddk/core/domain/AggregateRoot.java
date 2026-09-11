package com.ddk.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 聚合根：聚合的唯一入口，也是领域事件的收集点。
 * <p>
 * 「聚合边界」的实际含义是：外部只能拿到聚合根，聚合内部的实体与集合只暴露不可变视图，
 * 任何状态变更都必须经过聚合根上的领域方法，不变量才有唯一的守卫位置。
 *
 * <pre>{@code
 * public class Order extends AggregateRoot<OrderId> {
 *
 *     private final List<OrderLine> lines = new ArrayList<>();
 *     private OrderStatus status;
 *
 *     // 工厂方法而不是公开构造器：创建也是领域行为，也要保护不变量
 *     public static Order place(CustomerId customerId, List<OrderLine> lines) {
 *         if (lines == null || lines.isEmpty()) {
 *             throw new BusinessException(OrderError.EMPTY_ORDER);
 *         }
 *         Order order = new Order(customerId);
 *         lines.forEach(order::addLine);
 *         order.status = OrderStatus.PENDING_PAYMENT;
 *         order.registerEvent(new OrderPlacedEvent(customerId, order.totalAmount));
 *         return order;
 *     }
 *
 *     // 状态机约束写在实体里，而不是散落在 Service 中
 *     public void cancel(String reason) {
 *         if (!status.cancellable()) {
 *             throw new BusinessException(OrderError.NOT_CANCELLABLE, status);
 *         }
 *         this.status = OrderStatus.CANCELLED;
 *         registerEvent(new OrderCancelledEvent(id(), reason));
 *     }
 *
 *     // 对外暴露不可变视图，防止绕过聚合根直接改 lines
 *     public List<OrderLine> lines() {
 *         return Collections.unmodifiableList(lines);
 *     }
 * }
 * }</pre>
 *
 * @param <ID> 标识类型
 * @author Elijah Du
 * @date 2026/9/11
 */
public abstract class AggregateRoot<ID extends Identifier<?>> extends Entity<ID> {

    /**
     * 未发布的领域事件。
     * <p>
     * {@code transient}：事件是「本次操作产生的待办」，不属于聚合状态，
     * 不应该被持久化，也不应该跟着聚合被缓存。
     */
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 乐观锁版本号。
     * <p>
     * 放在聚合根而不是 {@link Entity} 上：并发控制的单位是聚合，不是聚合内的单个实体。
     * 由基础设施层映射到 PO 的版本列（MyBatis-Plus 的 {@code @Version}）。
     */
    private Long version;

    protected AggregateRoot() {
        super();
    }

    protected AggregateRoot(ID id) {
        super(id);
    }

    /**
     * 由子类在状态变更后调用，登记一个领域事件。
     * <p>
     * 只登记不发布：发布必须等事务提交，见 {@link DomainEventPublisher}。
     */
    protected void registerEvent(DomainEvent event) {
        Objects.requireNonNull(event, "领域事件不能为 null");
        domainEvents.add(event);
    }

    /**
     * 供基础设施层读取，返回不可变视图，防止外部篡改事件列表。
     */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 是否有待发布的事件。让基础设施层省掉一次列表拷贝。
     */
    public boolean hasDomainEvents() {
        return !domainEvents.isEmpty();
    }

    /**
     * 取出全部待发布事件并清空。
     * <p>
     * 这是推荐给基础设施层的用法：先复制再清空是原子的，
     * 避免「读取 → 发布 → 清空」过程中新登记的事件被误清。
     *
     * @return 本次取出的事件快照，不可变
     */
    public List<DomainEvent> drainDomainEvents() {
        List<DomainEvent> snapshot = List.copyOf(domainEvents);
        domainEvents.clear();
        return snapshot;
    }

    /**
     * 清空待发布事件。
     *
     * @see #drainDomainEvents() 通常应该用 drain 而不是它
     */
    public void clearEvents() {
        domainEvents.clear();
    }

    public Long version() {
        return version;
    }

    /**
     * 由基础设施层在加载聚合时回填版本号。
     */
    protected void assignVersion(Long version) {
        this.version = version;
    }
}
