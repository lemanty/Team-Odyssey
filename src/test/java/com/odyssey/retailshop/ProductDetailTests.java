package com.odyssey.retailshop;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.service.ProductService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductDetailTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProductService productService;

    @Test
    void showsPriceStockAndBothImagesWithEscapedName() throws Exception {
        when(productService.getProduct(42L)).thenReturn(Optional.of(new Product(42L,
            "Linen <Shirt>", "Clothing", "Soft linen.", new BigDecimal("39.00"), "Men", 12,
            "/images/products/sand-linen-shirt-product.webp", "/images/products/sand-linen-shirt-model-v2.webp")));
        mockMvc.perform(get("/products/42"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Linen &lt;Shirt&gt;")))
            .andExpect(content().string(containsString("€39.00")))
            .andExpect(content().string(containsString("12 available")))
            .andExpect(content().string(containsString("sand-linen-shirt-product.webp")))
            .andExpect(content().string(containsString("sand-linen-shirt-model-v2.webp")))
            .andExpect(content().string(containsString("data-next")))
            .andExpect(content().string(containsString("data-gallery-colour=\"Charcoal\"")))
            .andExpect(content().string(containsString("data-model=\"/images/products/variants/models/sand-linen-shirt-model-charcoal.png\"")))
            .andExpect(content().string(containsString("data-garment=\"/images/products/variants/sand-linen-shirt-charcoal.png\"")));
    }

    @Test
    void zeroStockIsOutOfStockRatherThanMissingStock() throws Exception {
        when(productService.getProduct(43L)).thenReturn(Optional.of(new Product(43L,
            "Tee", "Clothing", "Cotton", new BigDecimal("24.00"), "Women", 0, null, null)));
        mockMvc.perform(get("/products/43"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Out of stock")))
            .andExpect(content().string(containsString("Photography coming soon")));
    }

    @Test
    void householdUsesProductPhotoAndChineseCopyWithoutModelSlide() throws Exception {
        when(productService.getProduct(50L)).thenReturn(Optional.of(new Product(50L,
            "Ivory Ceramic Vase", "Household", "Sample", new BigDecimal("32.00"), "Home", 18,
            "/images/products/ivory-ceramic-vase.png", "/images/products/ivory-ceramic-vase.png")));
        mockMvc.perform(get("/products/50?lang=zh"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("米白陶瓷花瓶")))
            .andExpect(content().string(containsString("€32.00")))
            .andExpect(content().string(containsString("剩余 18 件")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-photo=\"1\""))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("模特穿搭图"))));
    }

    @Test
    void missingProductReturns404() throws Exception {
        when(productService.getProduct(999L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/products/999")).andExpect(status().isNotFound());
    }
}
