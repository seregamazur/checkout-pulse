package com.seregamazur.pulse.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seregamazur.pulse.inventory.dto.ProductCreateRequest;
import com.seregamazur.pulse.inventory.dto.ProductInput;
import com.seregamazur.pulse.inventory.dto.ProductResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/bulk")
    public ResponseEntity<List<ProductResponse>> createBulk(@RequestBody ProductCreateRequest request) {
        return ResponseEntity.ok(productService.createProducts(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id, @RequestBody ProductInput input) {
        return ResponseEntity.ok(productService.updateProduct(id, input));
    }
}
