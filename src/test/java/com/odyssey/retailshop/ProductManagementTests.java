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
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventory;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql") @AutoConfigureMockMvc
class ProductManagementTests {
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired ProductManagementService manager;
 @Autowired ProductService products; @Autowired OrderService orders;
 @BeforeEach void schema() throws Exception {
  jdbc.execute("DROP ALL OBJECTS");
  for(String file:List.of("001-products.sql","002-product-details.sql","003-users.sql","004-login-names.sql","005-orders.sql","011-wallet.sql","014-wallet-checkout.sql","015_returns.sql","017-coupons.sql","018-product-reviews.sql","019-review-discussions.sql","006-account-cart.sql","009-membership.sql","010-variants-addresses.sql","013-product-management.sql")) {
   String sql=Files.readString(Path.of("src/main/resources/db",file)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("(?m)^--.*$","");
   for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
  }
  jdbc.update("INSERT INTO users(id,display_name,email,password_hash,role) VALUES (1,'Customer','customer@test.com','unused','CUSTOMER'),(2,'Staff','staff@test.com','unused','STAFF'),(3,'Admin','admin@test.com','unused','ADMIN')");
 }
 ProductEditForm form(){var f=new ProductEditForm();f.setName("New vase");f.setNameZh("花瓶");f.setDescription("Handmade vase");f.setDescriptionZh("手工花瓶");f.setPrice(new BigDecimal("24.50"));f.setStock(4);f.setImage("/images/products/vase.png");f.setPublished(true);return f;}
 ProductEditForm.Option option(String size,int stock){var o=new ProductEditForm.Option();o.setSize(size);o.setColor("Green");o.setStock(stock);return o;}
 @Test void onlyAdminCanManageCatalogue() throws Exception {
  mvc.perform(get("/staff/products")).andExpect(status().is3xxRedirection());
  mvc.perform(get("/staff/products").with(user("customer@test.com").roles("CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(post("/staff/products/save").with(user("customer@test.com").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(get("/staff/products?lang=zh").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("商品与库存")));
  mvc.perform(get("/staff/products/new").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("mainPhoto")));
  mvc.perform(post("/staff/products/save").with(user("staff@test.com").roles("STAFF"))).andExpect(status().isForbidden());
 }
 @Test void staffCannotOpenOrSubmitCatalogueButCanOnlyChangeStock() throws Exception {
  long id=manager.save(form(),null,null);
  for(String path:List.of("/staff/products","/staff/products/new","/staff/products/"+id+"/edit"))
   mvc.perform(get(path).with(user("staff@test.com").roles("STAFF"))).andExpect(status().isForbidden());
  mvc.perform(post("/staff/products/save").with(user("staff@test.com").roles("STAFF")).with(csrf())
   .param("id",""+id).param("name","Hacked").param("published","false"))
   .andExpect(status().isForbidden());
  var original=manager.edit(id);
  mvc.perform(post("/staff/inventory").with(user("staff@test.com").roles("STAFF")).with(csrf())
   .param("productId",""+id).param("revision",original.getRevision()).param("quantity","7")
   .param("name","Hacked").param("price","0").param("published","false").param("options[0].size","XL"))
   .andExpect(status().isOk());
  var saved=manager.edit(id);assertEquals(7,saved.getStock());assertEquals(original.getName(),saved.getName());
  assertEquals(original.getPrice(),saved.getPrice());assertTrue(saved.getPublished());assertTrue(saved.getOptions().isEmpty());
  mvc.perform(get("/staff").with(user("staff@test.com").roles("STAFF"))).andExpect(status().isOk())
   .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/staff/products\""))))
   .andExpect(content().string(containsString("data-stock-tools")));
 }
 @Test void embeddedStockChecksRolesRevisionAndVariantTotals() throws Exception {
  var f=form();f.setCategory("Clothing");f.setAudience("Unisex");f.getOptions().add(option("M",2));f.getOptions().add(option("L",8));
  long id=manager.save(f,null,null);var original=manager.edit(id);
  mvc.perform(get("/staff/inventory").with(user("customer@test.com").roles("CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(post("/staff/inventory").with(user("customer@test.com").roles("CUSTOMER")).with(csrf()).param("productId",""+id).param("revision",original.getRevision())).andExpect(status().isForbidden());
  mvc.perform(get("/staff/inventory").param("productId",""+id).with(user("staff@test.com").roles("STAFF"))).andExpect(status().isOk()).andExpect(content().string(containsString("stock_"+original.getOptions().get(0).getId())));
  var stocks=new HashMap<Long,Integer>();for(var o:original.getOptions()) stocks.put(o.getId(),6);
  manager.updateStock(id,original.getRevision(),null,stocks);assertEquals(12,manager.edit(id).getStock());
  assertThrows(IllegalArgumentException.class,()->manager.updateStock(id,original.getRevision(),null,stocks));
  var fresh=manager.edit(id);stocks.put(fresh.getOptions().get(0).getId(),-1);
  assertThrows(IllegalArgumentException.class,()->manager.updateStock(id,fresh.getRevision(),null,stocks));assertEquals(12,manager.edit(id).getStock());
  stocks.clear();assertThrows(IllegalArgumentException.class,()->manager.updateStock(id,fresh.getRevision(),null,stocks));
  long plain=manager.save(form(),null,null);
  mvc.perform(post("/staff/inventory").with(user("admin@test.com").roles("ADMIN")).with(csrf()).param("productId",""+plain).param("revision",manager.edit(plain).getRevision()).param("quantity","19"))
    .andExpect(status().isOk()).andExpect(content().string(containsString("Stock saved")));
  assertEquals(19,manager.edit(plain).getStock());
  mvc.perform(post("/staff/inventory").with(user("staff@test.com").roles("STAFF")).param("productId",""+plain).param("revision",manager.edit(plain).getRevision()).param("quantity","20")).andExpect(status().isForbidden());
 }
 @Test void adminEmbeddedCreationAndWorkspaceRender() throws Exception {
  mvc.perform(get("/admin?lang=zh").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("admin-sidebar"))).andExpect(content().string(containsString("data-stock-tools")));
  mvc.perform(get("/admin?section=new").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("/admin/products/create")));
  mvc.perform(get("/staff").with(user("admin@test.com").roles("ADMIN"))).andExpect(redirectedUrl("/admin"));
  mvc.perform(get("/staff").with(user("staff@test.com").roles("STAFF"))).andExpect(status().isOk());
  mvc.perform(post("/admin/products/create").with(user("staff@test.com").roles("STAFF")).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(multipart("/admin/products/create").with(user("admin@test.com").roles("ADMIN")).with(csrf())
   .param("name","Admin vase").param("nameZh","管理员花瓶").param("description","Vase").param("descriptionZh","花瓶")
   .param("price","10.00").param("stock","5").param("category","Household").param("audience","Home"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin?section=new"));
  assertEquals(1,manager.list("Admin vase","draft").size());
  mvc.perform(multipart("/admin/products/create").with(user("admin@test.com").roles("ADMIN")).with(csrf()).param("name","Retained title"))
   .andExpect(status().isOk()).andExpect(content().string(containsString("Retained title")));
 }
 @Test void publishTranslationSearchUnpublishAndCheckoutProtection() throws Exception {
  long id=manager.save(form(),null,null);
  assertEquals("花瓶",products.getProduct(id).orElseThrow().getNameZh());
  mvc.perform(get("/products?lang=zh&q=手工")).andExpect(status().isOk()).andExpect(content().string(containsString("花瓶")));
  var f=manager.edit(id);f.setPublished(false);manager.save(f,null,null);
  assertTrue(products.getProduct(id).isEmpty());
  mvc.perform(get("/products/"+id)).andExpect(status().isNotFound());
  assertThrows(IllegalArgumentException.class,()->orders.place("customer@test.com",UUID.randomUUID().toString(),Map.of(id,1),"DELIVERY","Road"));
  assertEquals(4,manager.edit(id).getStock());
  assertEquals(1,manager.list("花瓶","draft").size());
 }
 @Test void optionsAggregateStockAndRejectStaleStockOrForeignIds() {
  var f=form();f.setCategory("Clothing");f.setAudience("Unisex");f.getOptions().add(option("M",2));f.getOptions().add(option("L",8));
  long id=manager.save(f,null,null);assertEquals(10,manager.edit(id).getStock());assertEquals(1,manager.list("","low").size());
  var stale=manager.edit(id);long variant=stale.getOptions().get(0).getId();
  String order=orders.place("customer@test.com",UUID.randomUUID().toString(),Map.of(-variant,1),"DELIVERY","Road");
  assertThrows(IllegalArgumentException.class,()->manager.save(stale,null,null));assertEquals(9,manager.edit(id).getStock());
  var fresh=manager.edit(id);fresh.getOptions().get(0).setStock(7);manager.save(fresh,null,null);assertEquals(15,manager.edit(id).getStock());
  var bad=manager.edit(id);bad.getOptions().get(0).setId(9999L);assertThrows(IllegalArgumentException.class,()->manager.save(bad,null,null));
  var hidden=manager.edit(id);hidden.setPublished(false);manager.save(hidden,null,null);
  orders.cancel(order,"customer@test.com");assertEquals(16,manager.edit(id).getStock());assertEquals("New vase",orders.items(order).get(0).productName());
 }
 @Test void rejectsInvalidInputsAndDoesNotPartiallySave() {
  var f=form();f.setPrice(new BigDecimal("1.111"));assertThrows(IllegalArgumentException.class,()->manager.save(f,null,null));
  f.setPrice(BigDecimal.ONE);f.setStock(-1);assertThrows(IllegalArgumentException.class,()->manager.save(f,null,null));
  f.setStock(1);f.setImage("javascript:alert(1)");assertThrows(IllegalArgumentException.class,()->manager.save(f,null,null));
  f.setImage("");assertThrows(IllegalArgumentException.class,()->manager.save(f,null,null));assertTrue(manager.list("","all").isEmpty());
 }
 @Test void imageUploadAndMultipartBindingAndEditorRender() throws Exception {
  var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
  var file=new MockMultipartFile("mainPhoto","photo.png","image/png",bytes.toByteArray());
  mvc.perform(multipart("/staff/products/save").file(file).with(user("admin@test.com").roles("ADMIN")).with(csrf())
    .param("name","Test shirt").param("nameZh","测试衬衫").param("description","Cotton").param("descriptionZh","棉")
    .param("price","19.50").param("category","Clothing").param("audience","Men").param("published","true")
    .param("options[0].size","S").param("options[0].color","White").param("options[0].stock","5"))
    .andExpect(status().is3xxRedirection());
  long id=manager.list("","all").get(0).id();var f=manager.edit(id);assertEquals(5,f.getStock());
  mvc.perform(get(f.getImage())).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes.toByteArray()));
  mvc.perform(get("/staff/products/"+id+"/edit?lang=zh").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("options[0].stock"))).andExpect(content().string(containsString("测试衬衫")));
  mvc.perform(get("/staff/products?lang=zh").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("需要补货")));
  assertThrows(IllegalArgumentException.class,()->manager.save(form(),new MockMultipartFile("mainPhoto","bad.png","image/png","not an image".getBytes()),null));
 }
}
