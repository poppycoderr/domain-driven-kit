package com.example.mall.order.application.service;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.page.PageResponse;
import com.example.mall.order.application.command.PlaceOrderCommand;
import com.example.mall.order.application.config.OrderProperties;
import com.example.mall.order.application.integration.OrderCancelledMessage;
import com.example.mall.order.application.query.OrderPageQuery;
import com.example.mall.order.application.response.OrderResponse;
import com.example.mall.order.domain.acl.OrderIdGenerator;
import com.example.mall.order.domain.acl.OrderRepository;
import com.example.mall.order.domain.acl.ProductCatalog;
import com.example.mall.order.domain.error.OrderError;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 订单应用服务：取出聚合、调用领域方法、保存、转换响应。下单规则和状态流转在 {@link Order} 里，这里只做编排与事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderIdGenerator orderIdGenerator;

    private final ProductCatalog productCatalog;

    private final ApplicationEventPublisher publisher;

    private final TransactionTemplate transaction;

    private final OrderProperties properties;

    @Transactional
    public OrderResponse place(PlaceOrderCommand command) {
        List<OrderLine> lines = command.lines().stream().map(this::toOrderLine).toList();
        Order order = Order.place(orderIdGenerator.nextId(), command.customerId(), lines, Instant.now().plus(properties.paymentTimeout()));
        return OrderResponse.from(orderRepository.create(order));
    }

    public OrderResponse get(Long customerId, Long orderId) {
        return OrderResponse.from(requireOwnOrder(customerId, orderId));
    }

    public PageResponse<OrderResponse> page(Long customerId, OrderPageQuery query) {
        query.setCustomerId(customerId);
        return orderRepository.page(query).map(orders -> orders.stream().map(OrderResponse::from).toList());
    }

    @Transactional
    public OrderResponse cancel(Long customerId, Long orderId, String reason) {
        Order order = requireOwnOrder(customerId, orderId);
        order.cancel(reason);
        return OrderResponse.from(orderRepository.update(order));
    }

    /**
     * 库存已预占。
     * <p>
     * 这条消息可能重复，也可能来得比顾客的取消还晚：消息的投递顺序没有保证，库存上下文可能先处理了「已取消」（那时还没有预占，
     * 什么都没释放），后处理「已下单」并占了库存。所以订单已经取消时，这里再发一次「已取消」，让库存把这次晚到的预占释放掉。
     * 流程因此不依赖消息的先后。
     */
    @Transactional
    public void confirmStock(Long orderId) {
        orderRepository.find(OrderId.of(orderId)).ifPresent(order -> {
            if (order.confirmStock()) {
                orderRepository.update(order);
            } else if (order.isCancelled()) {
                publisher.publishEvent(new OrderCancelledMessage(UUID.randomUUID().toString(), orderId, "订单已取消，释放晚到的库存预占"));
            }
        });
    }

    /**
     * 库存预占失败，订单随之取消。
     */
    @Transactional
    public void cancelForStock(Long orderId, String reason) {
        orderRepository.find(OrderId.of(orderId)).filter(Order::isOpen).ifPresent(order -> {
            order.cancel(reason);
            orderRepository.update(order);
        });
    }

    /**
     * 支付已完成。
     * <p>
     * 支付和取消可能同时发生：顾客付了款，而订单刚好被取消或超时关闭。以订单的状态为准，订单已经取消时再发一次「已取消」，
     * 支付上下文收到后会把这笔已经收到的钱退回去。这和 {@link #confirmStock} 处理晚到的预占是同一个办法。
     */
    @Transactional
    public void markPaid(Long orderId) {
        orderRepository.find(OrderId.of(orderId)).ifPresent(order -> {
            if (order.pay()) {
                orderRepository.update(order);
            } else if (order.isCancelled()) {
                publisher.publishEvent(new OrderCancelledMessage(UUID.randomUUID().toString(), orderId, "订单已取消，退回晚到的支付"));
            }
        });
    }

    /**
     * 关闭已经过了支付期限的订单，返回关闭的数量。
     * <p>
     * 每个订单在自己的事务里关闭：一个订单失败（例如顾客恰好在这一刻付了款，乐观锁冲突）不影响其他订单，它留到下一轮再看。
     */
    public int closeExpired(Instant now) {
        int closed = 0;
        for (OrderId id : orderRepository.findExpired(now, properties.closeBatchSize())) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> closeIfExpired(id, now)))) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not close expired order {}, leaving it for the next round", id.value(), e);
            }
        }
        return closed;
    }

    private boolean closeIfExpired(OrderId id, Instant now) {
        Order order = orderRepository.find(id).orElse(null);
        if (order == null || !order.isExpired(now)) {
            return false;
        }
        order.cancel("超过支付期限，订单已关闭");
        orderRepository.update(order);
        return true;
    }

    private OrderLine toOrderLine(PlaceOrderCommand.Line line) {
        ProductCatalog.Product product = productCatalog.find(line.skuId())
                .orElseThrow(() -> new BusinessException(OrderError.PRODUCT_NOT_FOUND, line.skuId()));
        return new OrderLine(product.skuId(), product.name(), product.unitPrice(), line.quantity());
    }

    /**
     * 别人的订单与不存在的订单一样处理，不向调用方透露这个订单号是否存在。
     */
    private Order requireOwnOrder(Long customerId, Long orderId) {
        return (orderId != null && orderId > 0 ? orderRepository.find(OrderId.of(orderId)) : Optional.<Order>empty())
                .filter(order -> order.belongsTo(customerId))
                .orElseThrow(() -> new BusinessException(OrderError.ORDER_NOT_FOUND, orderId));
    }
}
