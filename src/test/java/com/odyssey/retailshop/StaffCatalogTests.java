package com.odyssey.retailshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StaffCatalogTests {
    @Autowired MockMvc mvc;

    @Test void staffSeesCatalogueList() throws Exception {
        mvc.perform(get("/staff/products").with(user("staff").roles("STAFF")))
            .andExpect(status().isOk())
            .andExpect(view().name("staff-catalog"))
            .andExpect(model().attributeExists("products"));
    }

    @Test void staffOpensNewProductForm() throws Exception {
        mvc.perform(get("/staff/products/new").with(user("staff").roles("STAFF")))
            .andExpect(status().isOk())
            .andExpect(view().name("staff-product-form"));
    }

    @Test void staffOpensEditFormForExistingProduct() throws Exception {
        mvc.perform(get("/staff/products/1/edit").with(user("staff").roles("STAFF")))
            .andExpect(status().isOk())
            .andExpect(view().name("staff-product-form"));
    }

    @Test void adminAlsoReachesCatalogue() throws Exception {
        mvc.perform(get("/staff/products").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test void createProductRedirectsAndToggles() throws Exception {
        mvc.perform(post("/staff/products/new").with(user("staff").roles("STAFF")).with(csrf())
                .param("name", "Test Scarf").param("category", "Clothing").param("description", "A test product")
                .param("price", "30.00").param("stockQuantity", "5").param("audience", "Test").param("active", "true"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/staff/products/1/toggle").with(user("staff").roles("STAFF")).with(csrf()))
            .andExpect(status().is3xxRedirection());
    }

    @Test void invalidFormStaysOnPage() throws Exception {
        mvc.perform(post("/staff/products/new").with(user("staff").roles("STAFF")).with(csrf())
                .param("name", "").param("category", "").param("description", "")
                .param("price", "-1").param("stockQuantity", "-1"))
            .andExpect(status().isOk())
            .andExpect(view().name("staff-product-form"));
    }
}