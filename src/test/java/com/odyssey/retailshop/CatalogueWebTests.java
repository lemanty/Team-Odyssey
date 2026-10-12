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
    void catalogueRendersProductsThroughAllLayers() throws Exception {
        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Everyday Cotton Tee")))
            .andExpect(content().string(containsString("Stoneware Mug")))
            .andExpect(content().string(containsString("€16.50")))
            .andExpect(content().string(containsString("Sample catalogue")));
    }
}
