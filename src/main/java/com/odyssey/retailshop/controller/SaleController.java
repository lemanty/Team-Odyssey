package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.service.ProductService;
import com.odyssey.retailshop.service.SaleService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SaleController {
    private static final Map<Long, String> DEMO_IMAGES = Map.of(
        1L, "ivory-cotton-tee-product.webp",
        2L, "olive-overshirt-product.webp",
        3L, "sandstone-ceramic-mug.png",
        4L, "oatmeal-cotton-throw.png"
    );

    private final ProductService products;
    private final SaleService sales;

    public SaleController(ProductService products, SaleService sales) {
        this.products = products;
        this.sales = sales;
    }

    @GetMapping("/sale")
    public String sale(Model model) {
        var discounts = sales.activePrices().discounts();
        List<Offer> offers = products.getProducts().stream()
            .filter(product -> discounts.containsKey(product.getId()))
            .map(product -> offer(product, discounts.get(product.getId())))
            .sorted(java.util.Comparator.comparingInt(Offer::discount).reversed())
            .toList();

        model.addAttribute("saleDemo", !sales.available());
        model.addAttribute("offers", offers);
        model.addAttribute("featuredOffers", offers.stream().limit(3).toList());
        model.addAttribute("maxDiscount", offers.stream().mapToInt(Offer::discount).max().orElse(0));
        return "sale";
    }

    public record Offer(Product product, String title, int discount, BigDecimal price, String image, String featuredImage, String campaignImage) {}

    private Offer offer(Product product, SaleService.Discount discount) {
        String image = product.getGarmentImage();
        String featured = product.getModelImage();
        if (!sales.available()) {
            image = "/images/products/" + DEMO_IMAGES.get(product.getId());
            if (product.getId() == 1L) featured = "/images/products/ivory-cotton-tee-model-v2.webp";
            if (product.getId() == 2L) featured = "/images/products/olive-overshirt-model-v2.webp";
        }
        return new Offer(product, discount.title(), discount.percent(), discount.price(product.getPrice()),
            image, featured == null || featured.isBlank() ? image : featured, campaignImage(image));
    }
    // Only the known sample photographs have campaign editions. Uploaded images remain untouched.
    private String campaignImage(String image) {
        if (image == null) return null;
        return switch (image) {
            case "/images/products/olive-overshirt-product.webp" -> "/images/products/campaign-c/overshirt-hero.jpg";
            case "/images/products/ivory-cotton-tee-product.webp" -> "/images/products/campaign-c/tee-hero.jpg";
            case "/images/products/oatmeal-cotton-throw.png" -> "/images/products/campaign-c/throw-hero.jpg";
            default -> null;
        };
    }
}
