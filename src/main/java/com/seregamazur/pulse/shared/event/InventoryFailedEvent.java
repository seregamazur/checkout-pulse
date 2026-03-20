package com.seregamazur.pulse.shared.event;

import java.util.List;
import java.util.UUID;

public record InventoryFailedEvent(UUID orderId, List<OrderCreatedEvent.Item> items, String reason) {
}
