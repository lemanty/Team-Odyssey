package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.ProductService;
import org.springframework.stereotype.Controller;
import com.odyssey.retailshop.service.StoreText;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.text.Normalizer;
import com.odyssey.retailshop.model.Product;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Controller
public class ProductController {
    private final ProductService productService;
    private final Environment environment;
    private final StoreText storeText;
    private final com.odyssey.retailshop.service.VariantService variants;

    public ProductController(ProductService productService, Environment environment, StoreText storeText, com.odyssey.retailshop.service.VariantService variants) {
        this.productService = productService;
        this.environment = environment;
        this.storeText = storeText;
        this.variants=variants;
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getProduct(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")));
        model.addAttribute("variants",variants.list(id));
        return "product-detail";
    }

    @GetMapping("/products")
    public String products(Model model, HttpServletResponse response,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String minPrice,
            @RequestParam(defaultValue = "") String maxPrice,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "") String priceRange) {
        var all = productService.getProducts();
        var categories = all.stream().map(Product::getCategory).distinct().sorted().toList();
        model.addAttribute("categories", categories);
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        if (priceRange.isBlank()) priceRange = minPrice.isBlank() && maxPrice.isBlank() ? "all" : "custom";
        switch (priceRange) {
            case "under25" -> { minPrice = ""; maxPrice = "24.99"; }
            case "25to50" -> { minPrice = "25"; maxPrice = "49.99"; }
            case "50to100" -> { minPrice = "50"; maxPrice = "99.99"; }
            case "100plus" -> { minPrice = "100"; maxPrice = ""; }
            case "custom" -> { }
            default -> { priceRange = "all"; minPrice = ""; maxPrice = ""; }
        }
        model.addAttribute("priceRange", priceRange);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("sort", sort);
        boolean invalid = false;
        BigDecimal lower = null, upper = null;
        try {
            lower = price(minPrice);
            upper = price(maxPrice);
            invalid = lower != null && upper != null && lower.compareTo(upper) > 0;
        } catch (NumberFormatException ex) {
            invalid = true;
        }
        model.addAttribute("filterError", invalid);
        final BigDecimal min = lower, max = upper;
        var terms = normalize(q).split("\\s+");
        var filtered = all.stream()
            .filter(p -> category.isBlank() || category.equals(p.getCategory()))
            .filter(p -> java.util.Arrays.stream(terms).allMatch(searchable(p)::contains));
        if (!invalid) filtered = filtered.filter(p -> (min == null || p.getPrice().compareTo(min) >= 0)
                && (max == null || p.getPrice().compareTo(max) <= 0));
        if ("price-asc".equals(sort)) filtered = filtered.sorted(Comparator.comparing(Product::getPrice).thenComparing(Product::getId));
        if ("price-desc".equals(sort)) filtered = filtered.sorted(Comparator.comparing(Product::getPrice).reversed().thenComparing(Product::getId));
        model.addAttribute("products", invalid ? java.util.List.of() : filtered.toList());
        // The launcher only reuses a healthy catalogue running in database mode.
        response.setHeader("X-Odyssey-App", "retail-shop");
        response.setHeader("X-Odyssey-Mode", environment.acceptsProfiles(Profiles.of("mysql")) ? "mysql" : "demo");
        return "products";
    }
    private static BigDecimal price(String value) {
        if (value.isBlank()) return null;
        if (!value.trim().matches("[0-9]{1,9}(\\.[0-9]{1,2})?")) throw new NumberFormatException();
        return new BigDecimal(value.trim());
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
    }

    private String searchable(Product p) {
        return normalize(p.getName() + " " + p.getDescription() + " " + p.getCategory() + " "
            + storeText.text(p.getName(), true) + " " + storeText.text(p.getDescription(), true)
            + " " + storeText.text(p.getCategory(), true));
    }
}
