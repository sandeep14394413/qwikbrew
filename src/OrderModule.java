package com.qwikbrew.orderservice.controller;

import com.qwikbrew.orderservice.dto.*;
import com.qwikbrew.orderservice.model.Order;
import com.qwikbrew.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(req));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getByOrderNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getByOrderNumber(orderNumber));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderResponse>> getUserOrders(
            @PathVariable String userId,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrders(userId, pageable));
    }

    // ── Status transitions (Café staff / system) ──────────
    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirm(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.CONFIRMED));
    }

    @PatchMapping("/{orderId}/preparing")
    public ResponseEntity<OrderResponse> startPreparing(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.PREPARING));
    }

    @PatchMapping("/{orderId}/ready")
    public ResponseEntity<OrderResponse> markReady(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.READY));
    }

    @PatchMapping("/{orderId}/picked-up")
    public ResponseEntity<OrderResponse> markPickedUp(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.PICKED_UP));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable String orderId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, reason));
    }

    // ── Feedback ──────────────────────────────────────────
    @PostMapping("/{orderId}/feedback")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable String orderId,
            @Valid @RequestBody FeedbackRequest req) {
        orderService.submitFeedback(orderId, req);
        return ResponseEntity.ok().build();
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
package com.qwikbrew.orderservice.service;

import com.qwikbrew.orderservice.client.MenuServiceClient;
import com.qwikbrew.orderservice.client.PaymentServiceClient;
import com.qwikbrew.orderservice.client.UserServiceClient;
import com.qwikbrew.orderservice.dto.*;
import com.qwikbrew.orderservice.exception.OrderNotFoundException;
import com.qwikbrew.orderservice.model.Order;
import com.qwikbrew.orderservice.repository.OrderRepository;
import com.qwikbrew.orderservice.util.OrderNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor @Slf4j @Transactional
public class OrderService {

    private static final BigDecimal GST_RATE       = new BigDecimal("0.05");
    private static final BigDecimal POINTS_PER_RUPEE = new BigDecimal("0.10");

    private final OrderRepository          orderRepository;
    private final MenuServiceClient        menuClient;
    private final PaymentServiceClient     paymentClient;
    private final UserServiceClient        userClient;
    private final OrderNumberGenerator     orderNumberGen;
    private final ApplicationEventPublisher eventPublisher;

    public OrderResponse placeOrder(PlaceOrderRequest req) {
        // 1. Validate all menu items
        var items = menuClient.validateAndFetchItems(req.getItems());

        // 2. Compute pricing
        BigDecimal subtotal = items.stream()
                .map(i -> i.getUnitPrice().multiply(new BigDecimal(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gst      = subtotal.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = resolveDiscount(req.getUserId(), subtotal);
        BigDecimal total    = subtotal.add(gst).subtract(discount);

        // 3. Process payment
        paymentClient.charge(req.getUserId(), total, req.getPaymentMethod(), req.getPaymentReference());

        // 4. Build & persist order
        Order order = Order.builder()
                .orderNumber(orderNumberGen.generate())
                .userId(req.getUserId())
                .cafeId(req.getCafeId())
                .subtotal(subtotal)
                .gstAmount(gst)
                .discountAmount(discount)
                .totalAmount(total)
                .paymentMethod(req.getPaymentMethod())
                .specialInstructions(req.getSpecialInstructions())
                .brewPointsRedeemed(req.getBrewPointsToRedeem())
                .build();

        order = orderRepository.save(order);

        // 5. Award brew points (1 point per ₹10 spent)
        int pointsEarned = total.divide(BigDecimal.TEN, RoundingMode.FLOOR).intValue();
        userClient.earnPoints(req.getUserId(), pointsEarned, order.getId());
        order.setBrewPointsEarned(pointsEarned);
        orderRepository.save(order);

        // 6. Publish event → Notification service picks it up
        eventPublisher.publishEvent(new OrderPlacedEvent(order));

        log.info("Order {} placed for user {} — total ₹{}", order.getOrderNumber(), req.getUserId(), total);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        return OrderResponse.from(findOrThrow(orderId));
    }

    @Transactional(readOnly = true)
    public OrderResponse getByOrderNumber(String number) {
        return orderRepository.findByOrderNumber(number)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + number));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(String userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(OrderResponse::from);
    }

    public OrderResponse updateStatus(String orderId, Order.OrderStatus newStatus) {
        Order order = findOrThrow(orderId);
        order.setStatus(newStatus);
        switch (newStatus) {
            case CONFIRMED  -> order.setAcceptedAt(LocalDateTime.now());
            case READY      -> order.setReadyAt(LocalDateTime.now());
            case PICKED_UP  -> order.setPickedUpAt(LocalDateTime.now());
            default -> {}
        }
        order = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order));
        return OrderResponse.from(order);
    }

    public OrderResponse cancelOrder(String orderId, String reason) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() == Order.OrderStatus.PREPARING ||
            order.getStatus() == Order.OrderStatus.READY) {
            throw new IllegalStateException("Cannot cancel an order that is already being prepared");
        }
        order.setStatus(Order.OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        // Initiate refund via payment service
        paymentClient.refund(order.getUserId(), order.getTotalAmount(), order.getId());
        eventPublisher.publishEvent(new OrderCancelledEvent(order, reason));
        return OrderResponse.from(order);
    }

    public void submitFeedback(String orderId, FeedbackRequest req) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != Order.OrderStatus.PICKED_UP) {
            throw new IllegalStateException("Feedback allowed only after pickup");
        }
        eventPublisher.publishEvent(new FeedbackSubmittedEvent(order, req));
        log.info("Feedback {} ★ submitted for order {}", req.getRating(), orderId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private Order findOrThrow(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
    }

    private BigDecimal resolveDiscount(String userId, BigDecimal subtotal) {
        // Example: flat ₹20 corporate discount
        return subtotal.compareTo(new BigDecimal("100")) >= 0 ? new BigDecimal("20") : BigDecimal.ZERO;
    }
}
