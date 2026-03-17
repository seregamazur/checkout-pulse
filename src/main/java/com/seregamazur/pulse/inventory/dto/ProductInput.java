package com.seregamazur.pulse.inventory.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ProductInput(String name, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00") BigDecimal basePrice) {

}