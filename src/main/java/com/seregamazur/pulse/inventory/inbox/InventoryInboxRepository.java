package com.seregamazur.pulse.inventory.inbox;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryInboxRepository extends JpaRepository<InventoryInbox, UUID> {
}
