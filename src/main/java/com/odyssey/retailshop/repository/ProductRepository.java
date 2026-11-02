package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.model.ProductDraft;
import java.util.List;
import java.util.Optional;

/** Data access boundary; implemented by the demo or MySQL profile. */
public interface ProductRepository {
    /** Catalogue view: only products currently available for sale. */
    List<Product> findAll();

    /** Workspace view: every product, including those hidden from the catalogue. */
    List<Product> findAdminAll();

    default Optional<Product> findById(Long id) {
        return findAdminAll().stream().filter(product -> product.getId().equals(id)).findFirst();
    }

    long create(ProductDraft draft);

    boolean update(long id, ProductDraft draft);

    boolean setActive(long id, boolean active);

    boolean delete(long id);
}
