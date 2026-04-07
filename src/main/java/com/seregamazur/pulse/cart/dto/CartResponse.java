package com.seregamazur.pulse.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record CartResponse(
    UUID userId,
    List<CartItemDetailed> items,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    BigDecimal totalPrice
) {
}