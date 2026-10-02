package com.example.mall.order.application.service;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.page.PageResponse;
import com.example.mall.order.application.command.PlaceOrderCommand;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 订单应用服务：取出聚合、调用领域方法、保存、转换响应。下单规则和状态流转在 {@link Order} 里，这里只做编排与事务。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderIdGenerator orderIdGenerator;

    private final ProductCatalog productCatalog;

    @Transactional
    public OrderResponse place(PlaceOrderCommand command) {
        List<OrderLine> lines = command.lines().stream().map(this::toOrderLine).toList();
        Order order = Order.place(orderIdGenerator.nextId(), command.customerId(), lines);
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
