package com.seregamazur.pulse.inventory;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "products", schema = "inventory")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal basePrice;

    protected Product() {
    }

    public Product(String name, BigDecimal basePrice) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.basePrice = basePrice;
    }

    public void updateDetails(String name, BigDecimal basePrice) {
        this.name = name;
        this.basePrice = basePrice;
    }

}
