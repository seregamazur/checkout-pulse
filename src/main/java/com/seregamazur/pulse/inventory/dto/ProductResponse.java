package com.seregamazur.pulse.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.seregamazur.pulse.inventory.Product;

public record ProductResponse(UUID id, String name, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00") BigDecimal basePrice) {
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getBasePrice()
        );
    }
}
