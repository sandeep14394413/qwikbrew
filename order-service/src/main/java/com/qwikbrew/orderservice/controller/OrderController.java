package com.qwikbrew.orderservice.controller;

import com.qwikbrew.orderservice.dto.*;
import com.qwikbrew.orderservice.model.Order;
import com.qwikbrew.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("UP"); }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(req));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> get(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getByNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getByOrderNumber(orderNumber));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderResponse>> userOrders(
            @PathVariable String userId, Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrders(userId, pageable));
    }

    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirm(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.CONFIRMED));
    }

    @PatchMapping("/{orderId}/preparing")
    public ResponseEntity<OrderResponse> preparing(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.PREPARING));
    }

    @PatchMapping("/{orderId}/ready")
    public ResponseEntity<OrderResponse> ready(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.READY));
    }

    @PatchMapping("/{orderId}/picked-up")
    public ResponseEntity<OrderResponse> pickedUp(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, Order.OrderStatus.PICKED_UP));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable String orderId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, reason));
    }
}
