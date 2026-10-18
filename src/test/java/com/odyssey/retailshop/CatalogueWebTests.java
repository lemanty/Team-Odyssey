package com.odyssey.retailshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogueWebTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeRendersNavigationToCatalogue() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/products\"")));
    }

    @Test
    void homepageLanguageAndOrderNavigationCoexist() throws Exception {
        mockMvc.perform(get("/").param("lang", "zh")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("customer@test.com")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("日常好物，")))
            .andExpect(content().string(containsString("四位创始人，")))
            .andExpect(content().string(containsString("href=\"/orders\"")))
            .andExpect(content().string(containsString("?lang=en")))
            .andExpect(header().string("Set-Cookie", containsString("odyssey_language=zh")));
        mockMvc.perform(get("/").cookie(new jakarta.servlet.http.Cookie("odyssey_language", "zh")))
            .andExpect(content().string(containsString("日常好物，")));
        mockMvc.perform(get("/").param("lang", "en"))
            .andExpect(content().string(containsString("Good things.")))
            .andExpect(content().string(containsString("?lang=zh")));
    }

    @Test
    void languageSwitchIsAvailableAwayFromHomeAndPreservesQuery() throws Exception {
        for (String path : new String[]{"/products", "/cart", "/login", "/register"}) {
            mockMvc.perform(get(path + "?page=2&lang=zh"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"?page=2&amp;lang=en\"")))
                .andExpect(content().string(containsString("href=\"?page=2&amp;lang=zh\"")));
        }
    }

    @Test
    void chineseCatalogueTranslatesSampleProducts() throws Exception {
        mockMvc.perform(get("/products?lang=zh"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("全部商品")))
            .andExpect(content().string(containsString("日常纯棉T恤")))
            .andExpect(content().string(containsString("陶瓷马克杯")))
            .andExpect(content().string(containsString("/login?cartRequired=true")));
    }

    @Test
    void catalogueRendersProductsThroughAllLayers() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Everyday Cotton Tee")))
            .andExpect(content().string(containsString("Stoneware Mug")))
            .andExpect(content().string(containsString("€16.50")))
            .andExpect(content().string(containsString("Sample catalogue")));
    }

    @Test
    void searchCombinesBilingualPartialTermsCategoryAndInclusivePrice() throws Exception {
        mockMvc.perform(get("/products").param("q", "COT ton").param("category", "Clothing")
                .param("minPrice", "24").param("maxPrice", "24.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Everyday Cotton Tee")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Canvas Overshirt"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Woven Cotton Throw"))));
        mockMvc.perform(get("/products").param("q", "陶瓷").param("lang", "zh"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("陶瓷马克杯")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("日常纯棉T恤"))));
    }

    @Test
    void pricesSortAndEmptyOrInvalidFiltersAreExplained() throws Exception {
        String page = mockMvc.perform(get("/products").param("sort", "price-asc"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(page.indexOf("Stoneware Mug") < page.indexOf("Everyday Cotton Tee"));
        page = mockMvc.perform(get("/products").param("sort", "price-desc"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(page.indexOf("Canvas Overshirt") < page.indexOf("Woven Cotton Throw"));
        mockMvc.perform(get("/products").param("q", "nothing-matches"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("No matching products")));
        for (String min : new String[]{"30", "-1", "abc"}) {
            mockMvc.perform(get("/products").param("minPrice", min).param("maxPrice", "20"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Enter valid nonnegative prices")));
        }
    }
}
