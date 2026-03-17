package com.seregamazur.pulse.order.idempotency;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "idempotency_records", schema = "orders")
@Getter
public class IdempotencyRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    private Integer responseCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String responseBody;

    private LocalDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public static IdempotencyRecord createCompleted(UUID key, String body) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.id = key;
        record.status = IdempotencyStatus.COMPLETED;
        record.createdAt = LocalDateTime.now();
        record.responseCode = HttpStatus.CREATED.value();
        record.responseBody = body;
        return record;
    }

    public void markCompleted(int statusCode, String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseCode = statusCode;
        this.responseBody = responseBody;
    }

    public void markFailed() {
        this.status = IdempotencyStatus.FAILED;
    }

}

