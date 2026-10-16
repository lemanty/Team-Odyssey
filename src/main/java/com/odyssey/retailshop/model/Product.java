package com.odyssey.retailshop.model;

import java.math.BigDecimal;

/** Read-only product summary. SKU-level inventory will be added later. */
public class Product {
    private final Long id;
    private final String name;
    private final String category;
    private final String description;
    private final BigDecimal price;

    private final String audience;
    private final Integer stockQuantity;
    private final String garmentImage;
    private final String modelImage;

    public Product(Long id, String name, String category, String description, BigDecimal price) {
        this(id, name, category, description, price, null, null, null, null);
    }

    public Product(Long id, String name, String category, String description, BigDecimal price,
                   String audience, Integer stockQuantity, String garmentImage, String modelImage) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.audience = audience;
        this.stockQuantity = stockQuantity;
        this.garmentImage = garmentImage;
        this.modelImage = modelImage;
    }

    public String getAudience() { return audience; }
    public Integer getStockQuantity() { return stockQuantity; }
    public String getGarmentImage() { return garmentImage; }
    public String getModelImage() { return modelImage; }

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
