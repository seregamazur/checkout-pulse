package com.seregamazur.pulse.cart.dto;

import java.util.UUID;

public record CartItemRequest(UUID productId, int quantity) {

}



