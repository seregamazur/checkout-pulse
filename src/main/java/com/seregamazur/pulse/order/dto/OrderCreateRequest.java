package com.seregamazur.pulse.order.dto;

import java.util.UUID;

import com.seregamazur.pulse.cart.dto.CartResponse;

public record OrderCreateRequest(CartResponse cart, UUID idempotencyKey) {
}
