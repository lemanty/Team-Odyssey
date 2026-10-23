package com.odyssey.retailshop;

import com.odyssey.retailshop.service.OrderService;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql")
@AutoConfigureMockMvc
class OrderTests {
    @Autowired OrderService orders;
    @Autowired com.odyssey.retailshop.service.CartService cart;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        for (String name : List.of("001-products.sql","013-product-management.sql","002-product-details.sql","003-users.sql","005-orders.sql","011-wallet.sql","014-wallet-checkout.sql","015_returns.sql","017-coupons.sql","018-product-reviews.sql","009-membership.sql","010-variants-addresses.sql","006-account-cart.sql")) {
            String sql=Files.readString(Path.of("src/main/resources/db",name)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "").replaceAll("(?m)^--.*$", "");
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO users(id,display_name,email,password_hash) VALUES (1,'A','a@test.com','unused'),(2,'B','b@test.com','unused')");
        jdbc.update("INSERT INTO products(id,name,category,description,price) VALUES (1,'Shirt','Clothing','Cotton',12.50),(2,'Coat','Clothing','Wool',40)");
        jdbc.update("INSERT INTO product_details VALUES (1,'Men',3,'/shirt.webp','/model.webp'),(2,'Women',0,'/coat.webp','/model.webp')");
    }
    String place(String mode) { return orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(1L,2),mode,"Test address"); }
    int stock() { return jdbc.queryForObject("SELECT stock_quantity FROM product_details WHERE product_id=1",Integer.class); }
    @Test void checkoutSnapshotsRetryAndCancellation() {
        String token=UUID.randomUUID().toString();
        String id=orders.place("a@test.com",token,Map.of(1L,2),"DELIVERY","Road 1");
        assertEquals(1,stock());
        assertEquals(id,orders.place("a@test.com",token,Map.of(),"DELIVERY","Road 1"));
        jdbc.update("UPDATE products SET price=99,name='Changed' WHERE id=1");
        assertEquals("Shirt",orders.items(id).get(0).productName());
        assertEquals("25.00",orders.get(id,"a@test.com",false).total().toString());
        orders.edit(id,"a@test.com","COLLECTION","");
        orders.cancel(id,"a@test.com"); assertEquals(3,stock());
        assertThrows(IllegalArgumentException.class,()->orders.cancel(id,"a@test.com")); assertEquals(3,stock());
    }
    @Test void rollbackAndFulfillmentStateMachine() {
        assertThrows(IllegalArgumentException.class,()->orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(1L,2,2L,1),"DELIVERY","Road"));
        assertEquals(3,stock()); assertTrue(orders.list("a@test.com",false).isEmpty());
        String id=place("COLLECTION");
        assertThrows(IllegalArgumentException.class,()->orders.advance(id,"COMPLETED"));
        orders.advance(id,"PREPARING");
        assertThrows(IllegalArgumentException.class,()->orders.cancel(id,"a@test.com"));
        assertThrows(IllegalArgumentException.class,()->orders.edit(id,"a@test.com","DELIVERY","Road"));
        assertThrows(IllegalArgumentException.class,()->orders.advance(id,"SHIPPED"));
        orders.advance(id,"READY"); orders.advance(id,"COMPLETED"); assertEquals(1,stock());
    }
    @Test void competingCustomersCannotOversell() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> outcomes=new ArrayList<>();
            for (String email : List.of("a@test.com","b@test.com")) outcomes.add(pool.submit(()->{
                start.await(); try { orders.place(email,UUID.randomUUID().toString(),Map.of(1L,2),"COLLECTION",""); return true; }
                catch (IllegalArgumentException e) { return false; }
            }));
            start.countDown(); int successes=0; for (var future:outcomes) if (future.get(15,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes); assertEquals(1,stock());
        } finally { pool.shutdownNow(); }
    }
    @Test void accountCartPersistsAcrossSessionsAndIsIsolated() throws Exception {
        var first=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/cart/add").session(first).with(user("a@test.com")).with(csrf())
            .param("csrf",com.odyssey.retailshop.controller.CartController.token(first)).param("productId","1").param("quantity","2"))
            .andExpect(status().is3xxRedirection());
        var otherDevice=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/cart").session(otherDevice).with(user("a@test.com")))
            .andExpect(content().string(containsString("€25.00")));
        mvc.perform(get("/cart").session(first).with(user("b@test.com")))
            .andExpect(content().string(containsString("Your bag is empty")));
        mvc.perform(post("/logout").session(first).with(user("a@test.com")).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/cart").with(user("a@test.com")))
            .andExpect(content().string(containsString("€25.00")));
        assertEquals(2,jdbc.queryForObject("SELECT quantity FROM cart_items WHERE user_id=1",Integer.class));
    }
    @Test void failedCheckoutKeepsAccountCart() {
        jdbc.update("INSERT INTO cart_items VALUES (1,1,2),(1,2,1)");
        assertThrows(IllegalArgumentException.class,()->orders.placeFromCart("a@test.com",UUID.randomUUID().toString(),"COLLECTION",""));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class));
        assertEquals(3,stock());
    }
    @Test void checkoutThroughFormsClearsCartAndRetriesSafely() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession(); jdbc.update("INSERT INTO cart_items VALUES (1,1,2)");
        mvc.perform(get("/checkout").session(session).with(user("a@test.com"))).andExpect(status().isOk());
        String token=(String)session.getAttribute("checkoutToken");
        for (int retry=0; retry<2; retry++) mvc.perform(post("/orders").session(session).with(user("a@test.com")).with(csrf())
            .param("token",token).param("fulfillment","DELIVERY").param("address","Road 1")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)); assertEquals(1,stock()); assertEquals(1,orders.list("a@test.com",false).size());
        String id=orders.list("a@test.com",false).get(0).id();
        assertThrows(IllegalArgumentException.class,()->orders.confirmReceipt(id,"a@test.com"));
        orders.advance(id,"PREPARING"); orders.advance(id,"SHIPPED");
        assertThrows(IllegalArgumentException.class,()->orders.advance(id,"COMPLETED"));
        mvc.perform(get("/orders/"+id).with(user("a@test.com"))).andExpect(content().string(containsString("Confirm receipt")));
        mvc.perform(post("/orders/"+id+"/confirm-receipt").with(user("a@test.com"))).andExpect(status().isForbidden());
        mvc.perform(post("/orders/"+id+"/confirm-receipt").with(user("b@test.com")).with(csrf())).andExpect(status().isNotFound());
        assertEquals("SHIPPED",orders.get(id,"a@test.com",false).status());
        for(int i=0;i<2;i++) mvc.perform(post("/orders/"+id+"/confirm-receipt").with(user("a@test.com")).with(csrf())).andExpect(redirectedUrl("/orders/"+id));
        assertEquals("COMPLETED",orders.get(id,"a@test.com",false).status());
    }
    @Test void cancellationRacingPreparationHasOnlyOneWinner() throws Exception {
        String id=place("DELIVERY"); var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var cancel=pool.submit(()->{gate.await(); try { orders.cancel(id,"a@test.com"); return true; } catch(IllegalArgumentException e) { return false; }});
            var prepare=pool.submit(()->{gate.await(); try { orders.advance(id,"PREPARING"); return true; } catch(IllegalArgumentException e) { return false; }});
            gate.countDown(); boolean cancelled=cancel.get(15,TimeUnit.SECONDS); boolean prepared=prepare.get(15,TimeUnit.SECONDS);
            assertNotEquals(cancelled,prepared); assertEquals(cancelled ? 3 : 1,stock());
        } finally { pool.shutdownNow(); }
    }
    @Test void pagesOwnershipStaffPermissionsAndCsrf() throws Exception {
        String id=place("DELIVERY");
        var session=new org.springframework.mock.web.MockHttpSession();
        jdbc.update("INSERT INTO cart_items VALUES (1,1,1)");
        mvc.perform(get("/checkout").session(session).with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("Place order")));
        mvc.perform(get("/orders")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/orders/"+id).with(user("b@test.com"))).andExpect(status().isNotFound());
        mvc.perform(get("/orders?lang=zh").with(user("a@test.com")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Shirt")))
            .andExpect(content().string(containsString("/shirt.webp")))
            .andExpect(content().string(containsString("Test address")))
            .andExpect(content().string(containsString("配送地址")))
            .andExpect(content().string(containsString("status-pending")));
        assertTrue(orders.previews("b@test.com",false).isEmpty());

        mvc.perform(get("/staff/orders").with(user("a@test.com"))).andExpect(status().isForbidden());
        mvc.perform(post("/orders/"+id+"/cancel").with(user("a@test.com"))).andExpect(status().isForbidden());
        for (String path : List.of("/orders","/orders/"+id,"/checkout")) mvc.perform(get(path).with(user("a@test.com"))).andExpect(status().isOk());
        mvc.perform(get("/staff/orders/"+id).with(user("staff@test.com").roles("STAFF"))).andExpect(status().isOk()).andExpect(content().string(containsString("Start preparing")));
        mvc.perform(post("/staff/orders/"+id+"/status").with(user("a@test.com")).with(csrf()).param("next","PREPARING")).andExpect(status().isForbidden());
        mvc.perform(post("/staff/orders/"+id+"/status").with(user("staff@test.com").roles("STAFF")).with(csrf()).param("next","PREPARING")).andExpect(status().is3xxRedirection());
    }

    @Autowired com.odyssey.retailshop.service.MembershipService membership;
    void history(String status, String total) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES (?,1,?,?,'DELIVERY','Past',?)",id,id,status,new java.math.BigDecimal(total));
    }
    @Test void membershipThresholdsAndAccountIsolation() {
        history("PENDING","5000"); history("CANCELLED","5000");
        assertEquals("Essential",membership.forAccount("a@test.com").tier());
        history("COMPLETED","199.99");
        assertEquals("Essential",membership.forAccount("a@test.com").tier());
        history("COMPLETED","0.01");
        assertEquals("Silver",membership.forAccount("a@test.com").tier());
        history("COMPLETED","300");
        assertEquals("Gold",membership.forAccount("a@test.com").tier());
        history("COMPLETED","500");
        assertEquals("Signature",membership.forAccount("a@test.com").tier());
        assertEquals("Essential",membership.forAccount("b@test.com").tier());
    }
    @Test void discountSnapshotRoundingRetryAndCancellation() throws Exception {
        history("COMPLETED","200");
        String token=UUID.randomUUID().toString();
        String id=orders.place("a@test.com",token,Map.of(1L,1),"DELIVERY","Road");
        var order=orders.get(id,"a@test.com",false);
        assertEquals("11.88",order.total().toString());
        var snapshot=membership.snapshot(id,order.total());
        assertEquals("12.50",snapshot.original().toString());
        assertEquals("0.62",snapshot.discount().toString());
        assertEquals("Silver",snapshot.tier());
        history("COMPLETED","800");
        assertEquals(id,orders.place("a@test.com",token,Map.of(1L,1),"DELIVERY","Road"));
        assertEquals("Silver",membership.snapshot(id,order.total()).tier());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_membership WHERE order_id=?",Integer.class,id));
        mvc.perform(get("/orders/"+id+"?lang=zh").with(user("a@test.com")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("银卡会员")))
            .andExpect(content().string(containsString("11.88")));
        orders.cancel(id,"a@test.com");
        assertEquals(3,stock());
        assertEquals("1000.00",membership.forAccount("a@test.com").spent().toString());
    }
    @Test void checkoutUsesServerTierAndOlderOrdersKeepTheirTotal() throws Exception {
        history("COMPLETED","500");
        jdbc.update("INSERT INTO cart_items(user_id,product_id,quantity) VALUES (1,1,2)");
        mvc.perform(get("/checkout?lang=zh").with(user("a@test.com")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("金卡会员")))
            .andExpect(content().string(containsString("22.50")));
        var old=orders.list("a@test.com",false).get(0);
        assertEquals(0,membership.snapshot(old.id(),old.total()).discount().signum());
        assertEquals("500.00",membership.snapshot(old.id(),old.total()).payable().toString());
    }

    @Autowired com.odyssey.retailshop.service.AddressService addresses;
    void variants() {
        jdbc.update("INSERT INTO product_variants(id,product_id,size_label,color_label,stock_quantity) VALUES (101,1,'S','Original colour',1),(102,1,'M','Charcoal',2)");
    }
    @Test void variantLinesSnapshotsAndStockRestoration() throws Exception {
        variants();
        assertThrows(IllegalArgumentException.class,()->place("DELIVERY"));
        jdbc.update("INSERT INTO cart_variants VALUES (1,101,1),(1,102,1)");
        mvc.perform(get("/products/1?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("选择尺码")));
        mvc.perform(get("/cart?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("炭灰色")));
        String id=orders.placeFromCart("a@test.com",UUID.randomUUID().toString(),"DELIVERY","Road");
        assertEquals(2,orders.items(id).size()); assertEquals(1,stock());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cart_variants",Integer.class));
        jdbc.update("UPDATE product_variants SET color_label='Changed' WHERE id=102");
        assertTrue(orders.items(id).stream().anyMatch(i->i.color().equals("Charcoal")));
        for(String path:List.of("/orders","/orders/"+id)) mvc.perform(get(path+"?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("炭灰色")));
        orders.cancel(id,"a@test.com"); assertEquals(3,stock());
        assertEquals(1,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=101",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=102",Integer.class));
    }
    @Test void variantStockRollbackAndConcurrentLastItem() throws Exception {
        variants();
        assertThrows(IllegalArgumentException.class,()->orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(-101L,2,-102L,1),"COLLECTION",""));
        assertEquals(3,stock()); assertEquals(2,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=102",Integer.class));
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String email:List.of("a@test.com","b@test.com")) results.add(pool.submit(()->{gate.await();try{orders.place(email,UUID.randomUUID().toString(),Map.of(-101L,1),"COLLECTION","");return true;}catch(IllegalArgumentException e){return false;}}));
            gate.countDown(); int successes=0; for(var result:results)if(result.get(15,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);assertEquals(2,stock());
        } finally {pool.shutdownNow();}
    }
    @Test void addressDefaultsOwnershipAndCheckoutSnapshot() throws Exception {
        addresses.save("a@test.com",null,"Alice","123456","Original road",false);
        long first=addresses.list("a@test.com").get(0).id();
        addresses.save("a@test.com",null,"Alice","987654","Second road",true);
        assertEquals(1,addresses.list("a@test.com").stream().filter(a->a.isDefault()).count());
        addresses.setDefault("a@test.com",first);
        assertTrue(addresses.get("a@test.com",first).isDefault());
        mvc.perform(get("/account/addresses?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("Original road")));
        mvc.perform(get("/account/addresses?edit="+first).with(user("b@test.com"))).andExpect(status().isNotFound());
        mvc.perform(post("/account/addresses/"+first+"/delete").with(user("a@test.com"))).andExpect(status().isForbidden());
        mvc.perform(post("/account/addresses/"+first+"/default").with(user("b@test.com")).with(csrf())).andExpect(status().isNotFound());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->addresses.save("b@test.com",first,"B","123","Other",true));
        jdbc.update("INSERT INTO cart_items VALUES (1,1,1)");
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/checkout").session(session).with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("Original road")));
        mvc.perform(post("/orders").session(session).with(user("a@test.com")).with(csrf()).param("token",(String)session.getAttribute("checkoutToken")).param("fulfillment","DELIVERY").param("addressId",String.valueOf(first))).andExpect(redirectedUrl("/orders"));
        addresses.save("a@test.com",first,"Alice","000","Changed road",false);
        addresses.delete("a@test.com",first);
        assertTrue(addresses.list("a@test.com").get(0).isDefault());
        assertEquals("Alice · 123456\nOriginal road",orders.list("a@test.com",false).get(0).address());
        mvc.perform(post("/account/addresses").with(user("a@test.com")).with(csrf()).param("recipient","").param("phone","1").param("addressLine","Road")).andExpect(status().isOk()).andExpect(content().string(containsString("role=\"alert\"")));
    }
    @Test void cartRejectsForeignOptionsAndSeparatesChosenOptions() throws Exception {
        variants();
        var session=new org.springframework.mock.web.MockHttpSession();
        String token=com.odyssey.retailshop.controller.CartController.token(session);
        mvc.perform(post("/cart/add").session(session).with(user("a@test.com")).with(csrf()).param("csrf",token).param("productId","2").param("variantId","101"))
            .andExpect(redirectedUrl("/cart")).andExpect(flash().attributeExists("cartError"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cart_variants",Integer.class));
        for(String variant:List.of("101","102")) mvc.perform(post("/cart/add").session(session).with(user("a@test.com")).with(csrf()).param("csrf",token).param("productId","1").param("variantId",variant))
            .andExpect(redirectedUrl("/cart")).andExpect(flash().attributeExists("cartMessage"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM cart_variants WHERE user_id=1",Integer.class));
        mvc.perform(post("/cart/update").session(session).with(user("a@test.com")).with(csrf()).param("csrf",token).param("productId","-101").param("quantity","2"))
            .andExpect(flash().attributeExists("cartError"));
        assertEquals(1,jdbc.queryForObject("SELECT quantity FROM cart_variants WHERE variant_id=101",Integer.class));
        mvc.perform(get("/cart").with(user("b@test.com"))).andExpect(content().string(containsString("Your bag is empty")));
    }
}
