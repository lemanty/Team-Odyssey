package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import java.util.List;

/** Data access boundary; implemented by the demo or MySQL profile. */
public interface ProductRepository {
    List<Product> findAll();
}
