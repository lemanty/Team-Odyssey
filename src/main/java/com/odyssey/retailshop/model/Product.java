package com.odyssey.retailshop.model;

import java.math.BigDecimal;

/** Read-only product summary. SKU-level inventory will be added later. */
public class Product {
    private final Long id;
    private final String name;
    private final String category;
    private final String description;
    private final BigDecimal price;

    public Product(Long id, String name, String category, String description, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
