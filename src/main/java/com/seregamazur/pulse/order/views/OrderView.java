package com.seregamazur.pulse.order.views;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_views", schema = "orders")
public class OrderView {
    @Id
    private UUID orderId;
    private String displayStatus;
    private Instant lastUpdated;
}