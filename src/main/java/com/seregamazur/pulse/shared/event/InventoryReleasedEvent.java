package com.seregamazur.pulse.shared.event;

import java.util.List;
import java.util.UUID;

public record InventoryReleasedEvent(UUID orderId, List<OrderCreatedEvent.Item> items) {
}
