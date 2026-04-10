package com.seregamazur.pulse.cart;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    @Query("SELECT ci.productId, SUM(ci.quantity) FROM CartItem ci GROUP BY ci.productId")
    List<Object[]> sumQuantityByProductId();

    @Query("SELECT ci.productId, SUM(ci.quantity) FROM CartItem ci WHERE ci.updatedAt > :since AND ci.updatedAt < :before GROUP BY ci.productId")
    List<Object[]> sumQuantityByProductIdModifiedBetween(Instant since, Instant before);
}
