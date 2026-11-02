package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.model.ProductDraft;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Demo profile keeps a small in-memory catalogue; workspace edits apply to this list only. */
@Repository
@Profile("!mysql")
public class InMemoryProductRepository implements ProductRepository {
    private final List<Product> products = new ArrayList<>(List.of(
        new Product(1L, "Everyday Cotton Tee", "Clothing",
            "A comfortable everyday essential. Sizes and stock coming soon.", new BigDecimal("24.00")),
        new Product(2L, "Canvas Overshirt", "Clothing",
            "An easy layer for cooler days. Sample product for development.", new BigDecimal("58.00")),
        new Product(3L, "Stoneware Mug", "Household",
            "A simple companion for your morning coffee.", new BigDecimal("16.50")),
        new Product(4L, "Woven Cotton Throw", "Household",
            "A soft finishing touch for your living space.", new BigDecimal("42.00"))
    ));

    @Override
    public List<Product> findAll() {
        return products.stream().filter(Product::isActive).toList();
    }

    @Override
    public List<Product> findAdminAll() {
        return List.copyOf(products);
    }

    @Override
    public long create(ProductDraft draft) {
        long id = products.stream().mapToLong(p -> p.getId()).max().orElse(0L) + 1;
        products.add(toProduct(id, draft));
        return id;
    }

    @Override
    public boolean update(long id, ProductDraft draft) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getId() == id) {
                products.set(i, toProduct(id, draft));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setActive(long id, boolean active) {
        return findById(id).map(p -> update(id, new ProductDraft(p.getName(), p.getCategory(),
            p.getDescription(), p.getPrice(), p.getAudience(), p.getStockQuantity(),
            p.getGarmentImage(), p.getModelImage(), active))).orElse(false);
    }

    @Override
    public boolean delete(long id) {
        return products.removeIf(p -> p.getId() == id);
    }

    private static Product toProduct(long id, ProductDraft draft) {
        return new Product(id, draft.name(), draft.category(), draft.description(), draft.price(),
            draft.audience(), draft.stockQuantity(), draft.garmentImage(), draft.modelImage(), draft.active());
    }
}