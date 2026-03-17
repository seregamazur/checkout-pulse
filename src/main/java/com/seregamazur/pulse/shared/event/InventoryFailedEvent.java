package com.seregamazur.pulse.shared.event;

import java.util.UUID;

public record InventoryFailedEvent(UUID orderId, String reason) {
}
