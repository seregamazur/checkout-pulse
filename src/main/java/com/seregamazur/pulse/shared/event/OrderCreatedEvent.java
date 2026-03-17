package com.seregamazur.pulse.shared.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
    UUID orderId,
    List<Item> items,
    BigDecimal totalPrice
) {

    public record Item(
        UUID productId,
        int quantity
    ) {
    }
}
