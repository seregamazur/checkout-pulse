package com.seregamazur.pulse.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.seregamazur.pulse.order.Order;
import com.seregamazur.pulse.order.OrderStatus;

public record OrderResponse(
    UUID orderId,
    OrderStatus status,
    BigDecimal totalAmount,
    LocalDateTime createdAt
) {
    public static OrderResponse fromEntity(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getCreatedAt()
        );
    }
}