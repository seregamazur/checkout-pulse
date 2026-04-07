package com.seregamazur.pulse.cart;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "carts", schema = "inventory")
public class Cart {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public Cart(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
    }

    public void updateOrAddItem(UUID productId, long quantity) {
        Optional<CartItem> existingItem = items.stream()
            .filter(item -> item.getProductId().equals(productId))
            .findFirst();

        if (quantity <= 0) {
            existingItem.ifPresent(items::remove);
            return;
        }

        if (existingItem.isPresent()) {
            existingItem.get().updateQuantity(quantity);
        } else {
            CartItem newItem = new CartItem(this, productId, quantity);
            this.items.add(newItem);
        }
    }

    public void clear() {
        this.items.clear();
    }

}


