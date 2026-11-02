package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import java.util.List;
import java.util.Optional;

/** Data access boundary; implemented by the demo or MySQL profile. */
public interface ProductRepository {
    List<Product> findAll();

    default Optional<Product> findById(Long id) {
        return findAll().stream().filter(product -> product.getId().equals(id)).findFirst();
    }
}
