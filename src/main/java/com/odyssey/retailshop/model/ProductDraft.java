package com.odyssey.retailshop.model;

import java.math.BigDecimal;

/** Editable fields for creating or updating a catalogue product from the staff workspace. */
public record ProductDraft(
        String name,
        String category,
        String description,
        BigDecimal price,
        String audience,
        Integer stockQuantity,
        String garmentImage,
        String modelImage,
        boolean active) {
}