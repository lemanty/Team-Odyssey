package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import java.util.List;

/** Data access boundary; currently implemented with sample data. */
public interface ProductRepository {
    List<Product> findAll();
}
