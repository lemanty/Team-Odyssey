package com.odyssey.retailshop;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.service.ProductService;
import com.odyssey.retailshop.controller.CartController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Optional;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CartTests {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService products;
    private void product(Integer stock) {
        when(products.getProduct(42L)).thenReturn(Optional.of(new Product(42L, "Test shirt", "Clothing", "Cotton", new BigDecimal("12.50"), "Men", stock, null, null)));
    }
    private void change(MockHttpSession session, String action, int quantity) throws Exception {
        mvc.perform(post("/cart/"+action).session(session).with(csrf()).param("csrf",CartController.token(session)).param("productId","42").param("quantity",String.valueOf(quantity)))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
    }
    @Test void chineseCartIncludesProductLabelsFeedbackAndEmptyState() throws Exception {
        when(products.getProduct(42L)).thenReturn(Optional.of(new Product(42L,"Soft Knit Cardigan","Clothing","Cotton",new BigDecimal("49.00"),"Women",5,null,null)));
        MockHttpSession session = new MockHttpSession(); change(session,"add",1);
        mvc.perform(get("/cart?lang=zh").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("我的购物车")))
            .andExpect(content().string(containsString("柔软针织开衫")))
            .andExpect(content().string(containsString("商品小计")))
            .andExpect(content().string(containsString("数量")))
            .andExpect(content().string(containsString("更新")))
            .andExpect(content().string(containsString("继续选购")));
        mvc.perform(get("/cart?lang=zh").flashAttr("cartError","The requested quantity is not available. Please check the current stock."))
            .andExpect(content().string(containsString("所选数量超过可用库存")))
            .andExpect(content().string(containsString("购物车还是空的")));
        mvc.perform(get("/cart?lang=en").session(session)).andExpect(content().string(containsString("Soft Knit Cardigan")));
    }
    @Test void addMergeUpdateRemoveAndSessionIsolation() throws Exception {
        product(5); MockHttpSession session = new MockHttpSession();
        change(session,"add",1); change(session,"add",1);
        mvc.perform(get("/cart").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("€25.00")));
        mvc.perform(get("/cart").session(new MockHttpSession())).andExpect(content().string(containsString("Your bag is empty")));
        change(session,"update",3);
        mvc.perform(get("/cart").session(session)).andExpect(content().string(containsString("€37.50")));
        change(session,"remove",1);
        mvc.perform(get("/cart").session(session)).andExpect(content().string(containsString("Your bag is empty")));
    }
    @Test void rejectsStockOverflowAndInvalidQuantityWithoutChangingCart() throws Exception {
        product(2); MockHttpSession session = new MockHttpSession(); change(session,"add",2);
        for (int quantity : new int[]{1, 0, -1, 1000}) {
            mvc.perform(post("/cart/add").session(session).with(csrf()).param("csrf",CartController.token(session)).param("productId","42").param("quantity",String.valueOf(quantity)))
                .andExpect(flash().attributeExists("cartError"));
        }
        mvc.perform(get("/cart").session(session)).andExpect(content().string(containsString("€25.00")));
    }
    @Test void rejectsUnavailableAndForgedRequests() throws Exception {
        MockHttpSession session = new MockHttpSession();
        product(0);
        mvc.perform(post("/cart/add").session(session).with(csrf()).param("csrf",CartController.token(session)).param("productId","42"))
            .andExpect(flash().attributeExists("cartError"));
        product(null);
        mvc.perform(post("/cart/add").session(session).with(csrf()).param("csrf",CartController.token(session)).param("productId","42"))
            .andExpect(flash().attributeExists("cartError"));
        mvc.perform(post("/cart/add").session(session).with(csrf()).param("csrf","forged").param("productId","42"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/cart/add").session(session).with(csrf()).param("csrf",CartController.token(session)).param("productId","999"))
            .andExpect(flash().attributeExists("cartError"));
    }
    @Test void reflectsNewPriceAndStockAndAllowsRemovingDeletedProducts() throws Exception {
        MockHttpSession session = new MockHttpSession(); product(5); change(session,"add",2);
        product(1);
        mvc.perform(get("/cart").session(session)).andExpect(content().string(containsString("This quantity is no longer available")));
        when(products.getProduct(42L)).thenReturn(Optional.of(new Product(42L,"Test shirt","Clothing","Cotton",new BigDecimal("20.00"),"Men",5,null,null)));
        mvc.perform(get("/cart").session(session)).andExpect(content().string(containsString("€40.00")));
        when(products.getProduct(42L)).thenReturn(Optional.empty());
        mvc.perform(get("/cart").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("Product no longer available")));
        change(session,"remove",1);
        mvc.perform(get("/cart").session(session)).andExpect(content().string(containsString("Your bag is empty")));
    }
}
