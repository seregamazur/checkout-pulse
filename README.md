# E-Commerce Distributed Systems Playground

A backend application built to model a distributed e-commerce checkout process. The project uses a modular monolith approach to implement event-driven communication, distributed transactions, and concurrency control.

## Tech Stack

* **Java 21 & Spring Boot 4.0.3:** Core application logic.
* **Spring Modulith:** Logical separation of domains (Order, Inventory, Payment) sharing a single application context but isolated at the code level.
* **PostgreSQL:** Relational database for business entities, idempotency records, and Outbox/Inbox tables.
* **Redis:** Locks and fast-access idempotency cache.
* **RabbitMQ:** Message broker for asynchronous command/event routing between modules.
* **Flyway:** Database schema versioning.

## Architecture & Core Patterns

## Architecture & Workflows

### 1. Saga Choreography (Decentralized)
The system uses an event-driven flow where each module reacts to events without a central coordinator:
1.  **Order:** Creates `PENDING` order → Publishes `OrderCreated`.
2.  **Inventory:** Listens for `OrderCreated` → Reserves items → Publishes `InventoryReserved` (or `InventoryFailed`).
3.  **Payment:** Listens for `InventoryReserved` → Processes payment → Publishes `PaymentCompleted` (or `PaymentFailed`).
4.  **Order:** Listens for `PaymentCompleted` → Updates status to `SUCCESS`.

**Compensations:**
* **PaymentFailed** → **Inventory** catches it to release stock; **Order** catches it to mark as `CANCELLED`.
* **ReservationFailed** → **Order** catches it to mark as `CANCELLED`.

### 2. Reliable Messaging (Shared Outbox / Local Inbox)
* **Shared Outbox:** Located in the `shared` package. All modules write events into a single `outbox` table within their local business transaction.
* **Outbox Poller:** A centralized `Scheduled` task that reads the outbox and pushes messages to RabbitMQ.
* **Per-Module Inbox:** Each module (Order, Inventory, Payment) has its own `inbox` table to track processed `event_id`, ensuring **Exactly-Once** processing and idempotency at the consumer level.

### 3. Idempotency & Concurrency
* **Request Guard:** Order creation is protected by a two-tier check:
    1.  **Redis Lock:** Acquired via Redis using an idempotency key.
    2.  **DB Record:** A unique record in the database as a final safeguard against race conditions.

## TODO! Inventory Reservation Strategies (Strategy Pattern)

The system allows switching stock management logic dynamically:

* **Optimistic (Standard):** Uses JPA `@Version` for low contention.
* **Pessimistic (Black Friday):** Distributed Redis lock on `product_id`. Serializes access to prevent overselling during high load.
* **Post-Payment (High Load):** Stock is verified asynchronously after payment. If unavailable, an automated refund/compensation event is triggered.

## Project Structure

* `shared/`: Common events, DTOs, and the unified Outbox entity/poller.
* `order/`: Order lifecycle and status management.
* `inventory/`: Stock logic and reservation strategy implementations.
* `payment/`: Payment processing and failure events.
* `cart/`: Redis-backed shopping cart implementation.