package com.seregamazur.pulse.order.dto;

import java.util.UUID;


public record OrderCreateRequest(UUID cartId, UUID idempotencyKey) {
}
