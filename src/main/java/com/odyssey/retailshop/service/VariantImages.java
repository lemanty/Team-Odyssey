package com.odyssey.retailshop.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component("variantImages")
public class VariantImages {
    public record Colour(String color, java.util.List<VariantService.Variant> variants, int stock) {}
    public java.util.List<Colour> groups(java.util.List<VariantService.Variant> variants) {
        var groups = new java.util.LinkedHashMap<String,java.util.List<VariantService.Variant>>();
        for (var variant : variants) groups.computeIfAbsent(variant.color(), key -> new java.util.ArrayList<>()).add(variant);
        return groups.entrySet().stream().map(e -> new Colour(e.getKey(), java.util.List.copyOf(e.getValue()),
                e.getValue().stream().mapToInt(VariantService.Variant::stock).sum())).toList();
    }

    public String image(String original, String color) {
        if (original == null) return null;
        if ("Original colour".equalsIgnoreCase(color) || "原色".equals(color)) return original;
        if (("Charcoal".equalsIgnoreCase(color) || "炭灰色".equals(color))
                && original.matches("/images/products/[a-z0-9-]+-product\\.webp")) {
            String filename = original.substring(original.lastIndexOf('/') + 1).replace("-product.webp", "-charcoal.png");
            String path = "/images/products/variants/" + filename;
            if (new ClassPathResource("static" + path).exists()) return path;
        }
        return null;
    }
}
