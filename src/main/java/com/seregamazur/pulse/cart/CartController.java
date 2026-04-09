package com.seregamazur.pulse.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seregamazur.pulse.cart.dto.CartItemDetailed;
import com.seregamazur.pulse.cart.dto.CartItemRequest;
import com.seregamazur.pulse.cart.dto.CartResponse;
import com.seregamazur.pulse.inventory.ProductRepository;
import com.seregamazur.pulse.inventory.exception.ProductNotFoundException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final ProductRepository productRepository;

    @PutMapping("/{userId}/items")
    public ResponseEntity<Void> updateItem(@PathVariable UUID userId, @RequestBody CartItemRequest request) {
        try {
            cartService.updateItem(userId, request.productId(), request.quantity());
            return ResponseEntity.ok().build();
        } catch (ProductNotFoundException pe) {
            return ResponseEntity.status(404).build();
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable UUID userId) {
        var rawItems = cartService.getCartItems(userId);

        if (rawItems.isEmpty()) {
            return ResponseEntity.ok(new CartResponse(userId, List.of(), BigDecimal.ZERO));
        }

        List<UUID> productIds = rawItems.keySet().stream().toList();

        var products = productRepository.findAllById(productIds);

        List<CartItemDetailed> detailedItems = products.stream().map(p -> {
            long qty = rawItems.get(p.getId());
            BigDecimal subTotal = p.getBasePrice().multiply(BigDecimal.valueOf(qty));
            return new CartItemDetailed(p.getId(), p.getName(), qty, p.getBasePrice(), subTotal);
        }).toList();

        BigDecimal total = detailedItems.stream()
            .map(CartItemDetailed::subTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(new CartResponse(userId, detailedItems, total));
    }
}
