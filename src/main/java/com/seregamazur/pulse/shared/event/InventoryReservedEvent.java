package com.seregamazur.pulse.shared.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InventoryReservedEvent(UUID orderId,
                                     List<OrderCreatedEvent.Item> items,
                                     BigDecimal totalPrice) {
}
