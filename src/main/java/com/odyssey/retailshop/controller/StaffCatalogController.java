package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.model.ProductDraft;
import com.odyssey.retailshop.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Staff &amp; administrator catalogue operations; the routes sit under the role-restricted /staff path. */
@Controller
public class StaffCatalogController {
    private final ProductService products;
    public StaffCatalogController(ProductService products) { this.products = products; }

    public record CatalogForm(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String category,
            @NotBlank String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @Size(max = 20) String audience,
            @NotNull @Min(0) Integer stockQuantity,
            @Size(max = 255) String garmentImage,
            @Size(max = 255) String modelImage,
            boolean active) {
        ProductDraft toDraft() {
            return new ProductDraft(
                name.strip(), category.strip(), description.strip(), price,
                audience == null ? "" : audience.strip(), stockQuantity,
                blankToNull(garmentImage), blankToNull(modelImage), active);
        }
        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }

    @GetMapping("/staff/products")
    public String list(Model model) {
        model.addAttribute("products", products.getAdminProducts());
        return "staff-catalog";
    }

    @GetMapping("/staff/products/new")
    public String newForm(Model model) {
        model.addAttribute("form", new CatalogForm("", "", "", null, "", 0, "", "", true));
        model.addAttribute("editing", false);
        return "staff-product-form";
    }

    @PostMapping("/staff/products/new")
    public String create(@Valid @ModelAttribute("form") CatalogForm form, BindingResult result,
                         Model model, RedirectAttributes flash) {
        if (result.hasErrors()) { model.addAttribute("editing", false); model.addAttribute("validationError", true); model.addAttribute("form", form); return "staff-product-form"; }
        long id = products.create(form.toDraft());
        flash.addFlashAttribute("catalogMessage", "Product #" + id + " created.");
        return "redirect:/staff/products";
    }

    @GetMapping("/staff/products/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        Product p = products.getProduct(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        model.addAttribute("form", new CatalogForm(p.getName(), p.getCategory(), p.getDescription(),
            p.getPrice(), p.getAudience() == null ? "" : p.getAudience(),
            p.getStockQuantity() == null ? 0 : p.getStockQuantity(),
            p.getGarmentImage() == null ? "" : p.getGarmentImage(),
            p.getModelImage() == null ? "" : p.getModelImage(), p.isActive()));
        model.addAttribute("editing", true);
        model.addAttribute("productId", id);
        return "staff-product-form";
    }

    @PostMapping("/staff/products/{id}/edit")
    public String update(@PathVariable long id, @Valid @ModelAttribute("form") CatalogForm form,
                         BindingResult result, Model model, RedirectAttributes flash) {
        if (result.hasErrors()) { model.addAttribute("editing", true); model.addAttribute("productId", id); model.addAttribute("validationError", true); model.addAttribute("form", form); return "staff-product-form"; }
        if (!products.update(id, form.toDraft())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        flash.addFlashAttribute("catalogMessage", "Product #" + id + " updated.");
        return "redirect:/staff/products";
    }

    @PostMapping("/staff/products/{id}/toggle")
    public String toggle(@PathVariable long id, RedirectAttributes flash) {
        Product p = products.getProduct(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        boolean next = !p.isActive();
        products.setActive(id, next);
        flash.addFlashAttribute("catalogMessage", "Product #" + id + (next ? " is now live." : " is now hidden."));
        return "redirect:/staff/products";
    }

    @PostMapping("/staff/products/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes flash) {
        if (!products.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        flash.addFlashAttribute("catalogMessage", "Product #" + id + " deleted.");
        return "redirect:/staff/products";
    }
}