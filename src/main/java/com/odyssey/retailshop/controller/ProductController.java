package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.ProductService;
import com.odyssey.retailshop.service.SaleService;
import com.odyssey.retailshop.service.CatalogCategories;
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
    private final com.odyssey.retailshop.service.ReviewService reviews;
    private final Environment environment;
    private final StoreText storeText;
    private final com.odyssey.retailshop.service.VariantService variants;
    private final SaleService sales;

    public ProductController(ProductService productService, Environment environment, StoreText storeText, com.odyssey.retailshop.service.VariantService variants, com.odyssey.retailshop.service.ReviewService reviews, SaleService sales) {
        this.reviews=reviews;
        this.sales = sales;
        this.productService = productService;
        this.environment = environment;
        this.storeText = storeText;
        this.variants=variants;
    }

    @org.springframework.web.bind.annotation.ModelAttribute("shoppingAvailable")
    public boolean shoppingAvailable() {
        return environment.acceptsProfiles(Profiles.of("mysql","production"));
    }

    @org.springframework.web.bind.annotation.ModelAttribute("catalogueQuery")
    public String catalogueQuery(jakarta.servlet.http.HttpServletRequest request) {
        var query = org.springframework.web.util.UriComponentsBuilder.newInstance();
        for (String key : java.util.List.of("q","category","subcategory","priceRange","minPrice","maxPrice","sort","size","color","inStock","page")) {
            String value = request.getParameter(key);
            if (value != null && !value.isBlank()) query.queryParam(key, value);
        }
        return query.build().encode().toUriString();
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model, java.security.Principal user,
                                @RequestParam(defaultValue="0") int reviewPage,
                                @RequestParam(defaultValue="false") boolean sale,
                                @RequestParam(defaultValue="") String catalogue) {
        model.addAttribute("product", productService.getProduct(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")));
        model.addAttribute("fromSale", sale);
        model.addAttribute("backToCatalogue", sale ? "/sale" : "/products" +
            (catalogue.startsWith("?") ? catalogue : ""));
        if (sale) model.addAttribute("saleDiscount", sales.activePrices().discounts().get(id));
        model.addAttribute("variants",variants.list(id));
        String email=user==null?null:user.getName();
        reviewPage=Math.max(0,Math.min(reviewPage,100000));
        var reviewData=reviews.page(id,reviewPage);model.addAttribute("reviewData",reviewData);var replies=reviews.replies(id,reviewData.reviews());model.addAttribute("reviewReplies",replies);
        var replyTargets=new java.util.HashMap<Long,com.odyssey.retailshop.service.ReviewService.Review>();
        for(var root:reviewData.reviews())replyTargets.put(root.id(),root);
        for(var thread:replies.values())for(var reply:thread)replyTargets.put(reply.id(),reply);
        model.addAttribute("reviewTargets",replyTargets);model.addAttribute("reviewViewer",reviews.viewer(email));model.addAttribute("reviewPage",reviewPage);
        model.addAttribute("reviewsAvailable",reviews.available());
        model.addAttribute("reviewPurchases",reviews.purchases(id,email));
        return "product-detail";
    }

    @GetMapping("/products")
    public String products(Model model, HttpServletResponse response,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String subcategory,
            @RequestParam(defaultValue = "") String minPrice,
            @RequestParam(defaultValue = "") String maxPrice,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "") String priceRange,
            @RequestParam(defaultValue = "") String size,
            @RequestParam(defaultValue = "") String color,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(defaultValue = "1") int page) {
        var all = productService.getProducts();
        var visibleIds = all.stream().map(Product::getId).collect(java.util.stream.Collectors.toSet());
        var options = variants.all().stream().filter(v -> visibleIds.contains(v.productId())).toList();
        var byProduct = options.stream().collect(java.util.stream.Collectors.groupingBy(com.odyssey.retailshop.service.VariantService.Variant::productId));
        model.addAttribute("sizes", options.stream().map(v -> v.size()).distinct().sorted().toList());
        model.addAttribute("colors", options.stream().map(v -> v.color()).distinct().sorted().toList());
        model.addAttribute("size", size); model.addAttribute("color", color); model.addAttribute("inStock", inStock);
        var highestPrice = all.stream().map(Product::getPrice).filter(java.util.Objects::nonNull)
            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        model.addAttribute("priceSliderMax", highestPrice.divide(BigDecimal.valueOf(100), 0,
            java.math.RoundingMode.CEILING).max(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)));
        model.addAttribute("categories", CatalogCategories.categories());
        model.addAttribute("subcategories", CatalogCategories.subcategories(category));
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("subcategory", subcategory);
        if (priceRange.isBlank() || ("custom".equals(priceRange) && minPrice.isBlank() && maxPrice.isBlank())) priceRange = minPrice.isBlank() && maxPrice.isBlank() ? "all" : "custom";
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
        var filtered = all.stream().sorted(Comparator.comparing(Product::getId))
            .filter(p -> {
                var choices = byProduct.getOrDefault(p.getId(), java.util.List.of());
                if (choices.isEmpty()) return size.isBlank() && color.isBlank()
                    && (!inStock || (p.getStockQuantity()!=null && p.getStockQuantity()>0));
                return choices.stream().anyMatch(v -> (size.isBlank() || size.equals(v.size()))
                    && (color.isBlank() || color.equals(v.color()))
                    && (!inStock || (v.stock()>0 && p.getStockQuantity()!=null && p.getStockQuantity()>0)));
            })
            .filter(p -> CatalogCategories.matchesCategory(category, p.getCategory()))
            .filter(p -> subcategory.isBlank() || subcategory.equals(p.getSubcategory()))
            .filter(p -> java.util.Arrays.stream(terms).allMatch(searchable(p)::contains));
        if (!invalid) filtered = filtered.filter(p -> (min == null || p.getPrice().compareTo(min) >= 0)
                && (max == null || p.getPrice().compareTo(max) <= 0));
        if ("price-asc".equals(sort)) filtered = filtered.sorted(Comparator.comparing(Product::getPrice).thenComparing(Product::getId));
        if ("price-desc".equals(sort)) filtered = filtered.sorted(Comparator.comparing(Product::getPrice).reversed().thenComparing(Product::getId));
        var matches = invalid ? java.util.List.<Product>of() : filtered.toList();
        int totalPages = Math.max(1, (matches.size()+11)/12);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int start = (currentPage-1)*12;
        model.addAttribute("products", matches.subList(start, Math.min(start+12,matches.size())));
        model.addAttribute("totalProducts", matches.size());
        model.addAttribute("page", currentPage); model.addAttribute("totalPages", totalPages);
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
        return normalize(p.getName() + " " + p.getDescription() + " " + p.getCategory() + " " + p.getSubcategory() + " "
            + storeText.productName(p, true) + " " + storeText.productDescription(p, true)
            + " " + storeText.text(p.getCategory(), true));
    }
}
