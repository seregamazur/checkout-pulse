package com.seregamazur.pulse.payment.inbox;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentInboxRepository extends JpaRepository<PaymentInbox, UUID> {
}
