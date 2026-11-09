package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.service.ProductService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SaleController {
    private static final List<Selection> SELECTION = List.of(
        new Selection("Canvas Overshirt", 50, "olive-overshirt-product.webp", "olive-overshirt-model-v2.webp"),
        new Selection("Woven Cotton Throw", 35, "oatmeal-cotton-throw.png"),
        new Selection("Everyday Cotton Tee", 25, "ivory-cotton-tee-product.webp", "ivory-cotton-tee-model-v2.webp"),
        new Selection("Stoneware Mug", 20, "sandstone-ceramic-mug.png"),
        new Selection("Ivory Ceramic Vase", 30, "ivory-ceramic-vase.png"),
        new Selection("Sage Linen Cushion", 20, "sage-linen-cushion.png"),
        new Selection("Oatmeal Cotton Throw", 35, "oatmeal-cotton-throw.png"),
        new Selection("Sandstone Ceramic Mug", 20, "sandstone-ceramic-mug.png")
    );

    private final ProductService products;

    public SaleController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/sale")
    public String sale(Model model) {
        Map<String, Product> catalogue = products.getProducts().stream()
            .collect(Collectors.toMap(Product::getName, Function.identity(), (first, second) -> first));
        List<Offer> offers = SELECTION.stream()
            .filter(selection -> catalogue.containsKey(selection.name()))
            .map(selection -> selection.offer(catalogue.get(selection.name())))
            .toList();

        model.addAttribute("offers", offers);
        model.addAttribute("featuredOffers", offers.stream().limit(3).toList());
        model.addAttribute("maxDiscount", offers.stream().mapToInt(Offer::discount).max().orElse(0));
        return "sale";
    }

    // Illustrative prices for the sale design preview.
    public record Offer(Product product, int discount, BigDecimal previewPrice, String image, String featuredImage) {}

    private record Selection(String name, int discount, String image, String featuredImage) {
        Selection(String name, int discount, String image) {
            this(name, discount, image, image);
        }

        Offer offer(Product product) {
            BigDecimal previewPrice = product.getPrice().multiply(BigDecimal.valueOf(100L - discount, 2))
                .setScale(2, RoundingMode.HALF_UP);
            String imagePath = product.getGarmentImage() == null ? "/images/products/" + image : product.getGarmentImage();
            String featuredPath = product.getModelImage() == null ? "/images/products/" + featuredImage : product.getModelImage();
            return new Offer(product, discount, previewPrice, imagePath, featuredPath);
        }
    }
}
