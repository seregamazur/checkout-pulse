package com.seregamazur.pulse.order.inbox;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderInboxRepository extends JpaRepository<OrderInbox, UUID> {
}
