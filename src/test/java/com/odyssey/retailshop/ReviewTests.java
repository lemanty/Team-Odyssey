package com.odyssey.retailshop;

import com.odyssey.retailshop.service.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:reviews;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql") @AutoConfigureMockMvc
class ReviewTests {
 @Autowired ReviewService reviews;
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
 @BeforeEach void setup() throws Exception {
  jdbc.execute("DROP ALL OBJECTS");
  for(String file:List.of("001-products.sql","002-product-details.sql","003-users.sql","005-orders.sql","006-account-cart.sql","009-membership.sql","010-variants-addresses.sql","011-wallet.sql","013-product-management.sql","014-wallet-checkout.sql","015_returns.sql","017-coupons.sql","018-product-reviews.sql")) {
   String sql=Files.readString(Path.of("src/main/resources/db",file)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("(?m)^--.*$","");
   for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
  }
  jdbc.update("INSERT INTO users(id,display_name,email,password_hash) VALUES (1,'Alice','a@test.com','unused'),(2,'Bob','b@test.com','unused')");
  jdbc.update("INSERT INTO products(id,name,category,description,price) VALUES (1,'Vase','Household','Ceramic',12.50)");
  jdbc.update("INSERT INTO product_details VALUES (1,'Home',10,'/vase.png','')");
 }

 void purchase(String id,int uid,String status) {
  jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES (?,?,?,?, 'DELIVERY','Road',12.50)",id,uid,id,status);
  jdbc.update("INSERT INTO product_variants(id,product_id,size_label,color_label,stock_quantity) VALUES (1,1,'L','Green / 绿色',10) ON DUPLICATE KEY UPDATE stock_quantity=10");
  jdbc.update("INSERT INTO order_variant_items VALUES (?,1,1,'Vase',12.50,1,'M','White / 白色')",id);
 }
 @Test void verifiedSpecComesFromCompletedOrderSnapshot() {
  purchase("completed",1,"COMPLETED");reviews.save(1,"a@test.com",5,"Great","completed:1");
  var r=reviews.own(1,"a@test.com");assertTrue(r.purchased());assertEquals("M",r.size());assertEquals("White / 白色",r.color());
  reviews.save(1,"a@test.com",4,"Updated","");assertEquals(1,reviews.page(1,0).total());assertEquals("Updated",reviews.own(1,"a@test.com").body());
 }
 @Test void rejectsOtherUsersAndUncompletedPurchases() {
  purchase("foreign",2,"COMPLETED");purchase("pending",1,"SHIPPED");
  for(String key:List.of("foreign:1","pending:1","fake:1"))assertThrows(IllegalArgumentException.class,()->reviews.save(1,"a@test.com",5,"Fake",key));
  assertEquals(0,reviews.page(1,0).total());
  reviews.save(1,"a@test.com",3,"Not bought","");assertFalse(reviews.own(1,"a@test.com").purchased());
  reviews.delete(1,"b@test.com");assertEquals(1,reviews.page(1,0).total());reviews.delete(1,"a@test.com");assertEquals(0,reviews.page(1,0).total());
 }
 @Test void validatesRatingAndContent() {
  assertThrows(IllegalArgumentException.class,()->reviews.save(1,"a@test.com",6,"Hello",""));
  assertThrows(IllegalArgumentException.class,()->reviews.save(1,"a@test.com",5,"  ",""));
  assertThrows(IllegalArgumentException.class,()->reviews.save(1,"a@test.com",5,"a".repeat(2001),""));
 }
 @Test void webRendersVerifiedOptionsEscapesTextAndProtectsWrites() throws Exception {
  purchase("completed",1,"COMPLETED");reviews.save(1,"a@test.com",5,"<script>alert(1)</script>","completed:1");
  mvc.perform(get("/products/1?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("已购买"))).andExpect(content().string(containsString("M / 白色"))).andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(containsString("编辑我的评论")));
  mvc.perform(post("/products/1/reviews").with(user("a@test.com")).param("rating","5").param("body","Good")).andExpect(status().isForbidden());
  mvc.perform(post("/products/1/reviews").with(csrf()).param("rating","5").param("body","Good")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/products/1/reviews").with(user("b@test.com")).with(csrf()).param("rating","4").param("body","Nice")).andExpect(redirectedUrl("/products/1#reviews"));
  assertEquals(2,reviews.page(1,0).total());assertFalse(reviews.own(1,"b@test.com").purchased());
 }
}
