package com.seregamazur.pulse.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

    @Query("SELECT i FROM InventoryItem i WHERE i.updatedAt > :since AND i.updatedAt < :before")
    List<InventoryItem> findModifiedBetween(Instant since, Instant before);
}
