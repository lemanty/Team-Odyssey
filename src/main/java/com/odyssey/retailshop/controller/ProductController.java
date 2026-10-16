package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.ProductService;
import org.springframework.stereotype.Controller;
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

    public ProductController(ProductService productService, Environment environment) {
        this.productService = productService;
        this.environment = environment;
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getProduct(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")));
        return "product-detail";
    }

    @GetMapping("/products")
    public String products(Model model, HttpServletResponse response) {
        model.addAttribute("products", productService.getProducts());
        // The launcher only reuses a healthy catalogue running in database mode.
        response.setHeader("X-Odyssey-App", "retail-shop");
        response.setHeader("X-Odyssey-Mode", environment.acceptsProfiles(Profiles.of("mysql")) ? "mysql" : "demo");
        return "products";
    }
}
