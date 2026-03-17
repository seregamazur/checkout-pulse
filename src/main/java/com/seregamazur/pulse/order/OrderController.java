package com.seregamazur.pulse.order;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seregamazur.pulse.order.dto.OrderCreateRequest;
import com.seregamazur.pulse.order.dto.OrderResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderCreateRequest request) {
        if (!redisTemplate.opsForValue().setIfAbsent("lock:" + request.idempotencyKey(), "locked", Duration.ofSeconds(10))) {
            return ResponseEntity.status(409).build();
        }
        try {
            return ResponseEntity.ok().body(orderService.createOrder(request.cart(), request.idempotencyKey()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        } finally {
            redisTemplate.opsForValue().getAndDelete("lock:" + request.idempotencyKey());
        }
    }
}
