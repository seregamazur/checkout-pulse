package com.seregamazur.pulse.order.views.inbox;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderViewInboxRepository extends JpaRepository<OrderViewInbox, UUID> {
}
