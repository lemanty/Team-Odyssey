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
    void catalogueRendersProductsThroughAllLayers() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Everyday Cotton Tee")))
            .andExpect(content().string(containsString("Stoneware Mug")))
            .andExpect(content().string(containsString("€16.50")))
            .andExpect(content().string(containsString("Sample catalogue")));
    }
}
