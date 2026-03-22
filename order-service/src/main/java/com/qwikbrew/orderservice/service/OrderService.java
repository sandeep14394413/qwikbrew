package com.qwikbrew.orderservice.service;

import com.qwikbrew.orderservice.dto.*;
import com.qwikbrew.orderservice.event.OrderEventPublisher;
import com.qwikbrew.orderservice.model.Order;
import com.qwikbrew.orderservice.model.OrderItem;
import com.qwikbrew.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private static final BigDecimal GST_RATE       = new BigDecimal("0.05");
    private static final BigDecimal DISCOUNT_FLOOR = new BigDecimal("100");
    private static final BigDecimal FLAT_DISCOUNT  = new BigDecimal("20");

    private final OrderRepository       orderRepository;
    private final OrderEventPublisher   eventPublisher;

    private final AtomicInteger sequence = new AtomicInteger(1);

    public OrderResponse placeOrder(PlaceOrderRequest req) {
        List<OrderItem> orderItems = req.getItems().stream()
            .map(i -> OrderItem.builder()
                .menuItemId(i.getMenuItemId())
                .itemName(i.getItemName())
                .unitPrice(i.getUnitPrice())
                .quantity(i.getQuantity())
                .lineTotal(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .selectedAddOns(i.getSelectedAddOns())
                .customization(i.getCustomization())
                .build())
            .toList();

        // Build order items and subtotal
        BigDecimal subtotal = orderItems.stream()
            .map(OrderItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gst      = subtotal.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = subtotal.compareTo(DISCOUNT_FLOOR) >= 0 ? FLAT_DISCOUNT : BigDecimal.ZERO;
        BigDecimal total    = subtotal.add(gst).subtract(discount).setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
            .orderNumber(generateOrderNumber())
            .userId(req.getUserId())
            .cafeId(req.getCafeId() != null ? req.getCafeId() : "CAFE-001")
            .subtotal(subtotal)
            .gstAmount(gst)
            .discountAmount(discount)
            .totalAmount(total)
            .paymentMethod(req.getPaymentMethod())
            .paymentReference(req.getPaymentReference())
            .specialInstructions(req.getSpecialInstructions())
            .brewPointsRedeemed(req.getBrewPointsToRedeem() != null ? req.getBrewPointsToRedeem() : 0)
            .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        order = orderRepository.save(order);

        // Earn 1 BrewPoint per ₹10 spent
        int pointsEarned = total.divide(BigDecimal.TEN, RoundingMode.FLOOR).intValue();
        order.setBrewPointsEarned(pointsEarned);
        order = orderRepository.save(order);

        eventPublisher.publishOrderPlaced(order);
        log.info("Order {} placed for user {} — ₹{}", order.getOrderNumber(), req.getUserId(), total);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        return OrderResponse.from(find(orderId));
    }

    @Transactional(readOnly = true)
    public OrderResponse getByOrderNumber(String number) {
        return orderRepository.findByOrderNumber(number)
            .map(OrderResponse::from)
            .orElseThrow(() -> new RuntimeException("Order not found: " + number));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(String userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(OrderResponse::from);
    }

    public OrderResponse updateStatus(String orderId, Order.OrderStatus newStatus) {
        Order order = find(orderId);
        order.setStatus(newStatus);
        switch (newStatus) {
            case CONFIRMED  -> order.setAcceptedAt(LocalDateTime.now());
            case READY      -> order.setReadyAt(LocalDateTime.now());
            case PICKED_UP  -> order.setPickedUpAt(LocalDateTime.now());
            default         -> {}
        }
        order = orderRepository.save(order);
        eventPublisher.publishStatusChanged(order);
        return OrderResponse.from(order);
    }

    public OrderResponse cancelOrder(String orderId, String reason) {
        Order order = find(orderId);
        if (List.of(Order.OrderStatus.PREPARING, Order.OrderStatus.READY).contains(order.getStatus()))
            throw new IllegalStateException("Cannot cancel an order that is already being prepared");
        order.setStatus(Order.OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        eventPublisher.publishOrderCancelled(order, reason);
        return OrderResponse.from(order);
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private Order find(String id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    private String generateOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("%s-%s-%04d",
            System.getenv().getOrDefault("ORDER_NUMBER_PREFIX", "QBR"),
            datePart,
            sequence.getAndIncrement());
    }
}
