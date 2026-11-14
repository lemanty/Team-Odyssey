package com.odyssey.retailshop;

import com.odyssey.retailshop.service.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:favorites;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql") @AutoConfigureMockMvc
class FavoriteTests {
 @Autowired FavoriteService favorites; @Autowired ProductManagementService manager;
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
 @BeforeEach void setup() throws Exception {
  jdbc.execute("DROP ALL OBJECTS");
  for(String file:List.of("001-products.sql","002-product-details.sql","003-users.sql","004-login-names.sql","005-orders.sql","022-sale-campaigns.sql","023-sale-featured.sql","011-wallet.sql","014-wallet-checkout.sql","015_returns.sql","017-coupons.sql","018-product-reviews.sql","019-review-discussions.sql","024-review-images.sql","025-favorites-notifications.sql","006-account-cart.sql","009-membership.sql","010-variants-addresses.sql","013-product-management.sql")) {
   String sql=Files.readString(Path.of("src/main/resources/db",file)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("(?m)^--.*$","");
   for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
  }
  jdbc.update("INSERT INTO users(id,display_name,email,password_hash,role) VALUES (1,'Alice','a@test.com','unused','CUSTOMER'),(2,'Bob','b@test.com','unused','CUSTOMER')");
 }
 ProductEditForm form(int stock,boolean published){
  var f=new ProductEditForm();f.setName("Vase");f.setNameZh("花瓶");f.setDescription("Ceramic vase");f.setDescriptionZh("陶瓷花瓶");
  f.setPrice(new BigDecimal("12.50"));f.setStock(stock);f.setImage("/images/products/vase.png");f.setPublished(published);f.setSubcategory("living-room");return f;
 }
 @Test void favouritesArePerAccountAndToggle() {
  long id=manager.save(form(0,true),null,null);
  assertTrue(favorites.toggle(id,"a@test.com"));
  assertTrue(favorites.favorited(id,"a@test.com"));
  assertFalse(favorites.favorited(id,"b@test.com"));
  assertEquals(1,favorites.favorites("a@test.com").size());
  assertFalse(favorites.toggle(id,"a@test.com"));
  assertFalse(favorites.favorited(id,"a@test.com"));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->favorites.toggle(9999,"a@test.com"));
 }
 @Test void restockAlertsOnlyFavoritersOncePerUnreadOption() {
  long id=manager.save(form(0,true),null,null);
  favorites.toggle(id,"a@test.com");favorites.toggle(id,"b@test.com");
  manager.updateStock(id,manager.edit(id).getRevision(),6,Map.of());
  var alerts=favorites.notifications("a@test.com");
  assertEquals(1,alerts.size());assertFalse(alerts.get(0).read());assertEquals(1,favorites.unread("a@test.com"));
  // Going out of stock and back while the alert is unread must not duplicate it.
  manager.updateStock(id,manager.edit(id).getRevision(),0,Map.of());
  manager.updateStock(id,manager.edit(id).getRevision(),6,Map.of());
  assertEquals(1,favorites.notifications("a@test.com").size());
  favorites.markAllRead("a@test.com");
  assertEquals(0,favorites.unread("a@test.com"));
  assertEquals(1,favorites.unread("b@test.com"));
  // After reading, the next arrival is announced again.
  manager.updateStock(id,manager.edit(id).getRevision(),0,Map.of());
  manager.updateStock(id,manager.edit(id).getRevision(),8,Map.of());
  assertEquals(2,favorites.notifications("a@test.com").size());
  long latest=favorites.notifications("a@test.com").get(0).id();
  favorites.markRead(latest,"b@test.com");
  assertEquals(1,favorites.unread("a@test.com"));
 }
 @Test void alertsRequirePublishedProductAndZeroToPositiveStock() {
  long draft=manager.save(form(0,false),null,null);
  favorites.toggle(draft,"a@test.com");
  manager.updateStock(draft,manager.edit(draft).getRevision(),5,Map.of());
  assertEquals(0,favorites.notifications("a@test.com").size());
  var publish=manager.edit(draft);publish.setPublished(true);manager.save(publish,null,null);
  assertEquals(0,favorites.notifications("a@test.com").size());
  manager.updateStock(draft,manager.edit(draft).getRevision(),0,Map.of());
  manager.updateStock(draft,manager.edit(draft).getRevision(),3,Map.of());
  assertEquals(1,favorites.notifications("a@test.com").size());
  long id=manager.save(form(9,true),null,null);
  favorites.toggle(id,"a@test.com");
  manager.updateStock(id,manager.edit(id).getRevision(),10,Map.of());
  assertEquals(1,favorites.notifications("a@test.com").size());
 }
 @Test void pagesRequireLoginAndRenderSavedProductsAndAlerts() throws Exception {
  long id=manager.save(form(0,true),null,null);
  mvc.perform(post("/favorites/"+id).with(csrf())).andExpect(status().is3xxRedirection());
  mvc.perform(get("/account/favorites")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/favorites/"+id).with(user("a@test.com")).with(csrf())).andExpect(status().is3xxRedirection());
  manager.updateStock(id,manager.edit(id).getRevision(),4,Map.of());
  mvc.perform(get("/products/"+id+"?lang=zh").with(user("a@test.com"))).andExpect(status().isOk())
   .andExpect(content().string(containsString("已收藏"))).andExpect(content().string(containsString("补货")));
  mvc.perform(get("/account/favorites?lang=zh").with(user("a@test.com"))).andExpect(status().isOk())
   .andExpect(content().string(containsString("我的收藏"))).andExpect(content().string(containsString("花瓶")))
   .andExpect(content().string(containsString("到货提醒"))).andExpect(content().string(containsString("未读提醒")));
  long unread=favorites.notifications("a@test.com").get(0).id();
  mvc.perform(post("/account/notifications/"+unread+"/read").with(user("a@test.com")).with(csrf())).andExpect(status().is3xxRedirection());
  assertEquals(0,favorites.unread("a@test.com"));
 }
}
