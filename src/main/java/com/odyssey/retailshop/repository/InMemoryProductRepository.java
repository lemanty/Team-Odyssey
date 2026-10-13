package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final List<Product> products = List.of(
        new Product(1L, "Everyday Cotton Tee", "Clothing",
            "A comfortable everyday essential. Sizes and stock coming soon.", new BigDecimal("24.00")),
        new Product(2L, "Canvas Overshirt", "Clothing",
            "An easy layer for cooler days. Sample product for development.", new BigDecimal("58.00")),
        new Product(3L, "Stoneware Mug", "Household",
            "A simple companion for your morning coffee.", new BigDecimal("16.50")),
        new Product(4L, "Woven Cotton Throw", "Household",
            "A soft finishing touch for your living space.", new BigDecimal("42.00"))
    );

    @Override
    public List<Product> findAll() {
        return products;
    }
}
