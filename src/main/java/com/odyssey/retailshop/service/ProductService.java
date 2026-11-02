package com.odyssey.retailshop.service;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.model.ProductDraft;
import com.odyssey.retailshop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Optional<Product> getProduct(Long id) {
        return productRepository.findById(id);
    }

    public List<Product> getProducts() {
        return productRepository.findAll();
    }

    public List<Product> getAdminProducts() {
        return productRepository.findAdminAll();
    }

    public long create(ProductDraft draft) {
        return productRepository.create(draft);
    }

    public boolean update(long id, ProductDraft draft) {
        return productRepository.update(id, draft);
    }

    public boolean setActive(long id, boolean active) {
        return productRepository.setActive(id, active);
    }

    public boolean delete(long id) {
        return productRepository.delete(id);
    }
}
