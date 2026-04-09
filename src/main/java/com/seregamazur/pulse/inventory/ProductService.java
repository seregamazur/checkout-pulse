package com.seregamazur.pulse.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seregamazur.pulse.inventory.dto.ProductCreateRequest;
import com.seregamazur.pulse.inventory.dto.ProductInput;
import com.seregamazur.pulse.inventory.dto.ProductResponse;
import com.seregamazur.pulse.inventory.exception.ProductNotFoundException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public List<ProductResponse> createProducts(ProductCreateRequest request) {
        List<Product> products = request.products().stream()
            .map(input -> new Product(input.name(), input.basePrice()))
            .toList();

        List<Product> savedProducts = productRepository.saveAll(products);

        List<InventoryItem> inventories = savedProducts.stream()
            .map(p -> new InventoryItem(p.getId(), 0))
            .toList();

        inventoryRepository.saveAll(inventories);

        return savedProducts.stream()
            .map(ProductResponse::fromEntity)
            .toList();
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductInput input) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id.toString()));

        product.updateDetails(input.name(), input.basePrice());
        return ProductResponse.fromEntity(product);
    }
}