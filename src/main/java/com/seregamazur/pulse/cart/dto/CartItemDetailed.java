package com.seregamazur.pulse.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record CartItemDetailed(
    UUID productId,
    String name,
    int quantity,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    BigDecimal price,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    BigDecimal subTotal //quantity * price
) {

    public CartItemDetailed {
        if (quantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
    }
}