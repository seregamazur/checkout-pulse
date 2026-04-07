package com.seregamazur.pulse.infra.inbox;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RedisStockInboxRepository extends JpaRepository<RedisStockInbox, UUID> {
}
