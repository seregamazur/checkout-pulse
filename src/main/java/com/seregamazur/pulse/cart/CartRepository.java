package com.seregamazur.pulse.cart;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserId(UUID userId);

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items")
    List<Cart> findAllWithItems();

    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i WHERE i.updatedAt > :since AND i.updatedAt < :before")
    List<Cart> findWithItemsModifiedBetween(Instant since, Instant before);
}
