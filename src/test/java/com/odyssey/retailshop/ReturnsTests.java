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
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:returns;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql") @AutoConfigureMockMvc
class ReturnsTests {
 @Autowired ReturnService returns;@Autowired OrderService orders;@Autowired WalletService wallet;@Autowired SupportService support;@Autowired OrderWorkspaceService workspace;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
 @BeforeEach void setup()throws Exception {
  jdbc.execute("DROP ALL OBJECTS");
  for(String file:List.of("001-products.sql","002-product-details.sql","003-users.sql","005-orders.sql","006-account-cart.sql","007-support.sql","008-support-attachments.sql","009-membership.sql","010-variants-addresses.sql","011-wallet.sql","013-product-management.sql","014-wallet-checkout.sql","015_returns.sql","016_support_returns.sql")){
   String sql=Files.readString(Path.of("src/main/resources/db",file)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("(?m)^--.*$","");for(String st:sql.split(";"))if(!st.isBlank())jdbc.execute(st);
  }
  jdbc.update("INSERT INTO users(id,display_name,email,password_hash,role) VALUES (1,'Alice','a@test.com','unused','CUSTOMER'),(2,'Bob','b@test.com','unused','CUSTOMER'),(3,'Employee','staff@test.com','unused','STAFF')");
  jdbc.update("INSERT INTO products(id,name,category,description,price) VALUES (1,'Vase','Household','Ceramic',12.50)");jdbc.update("INSERT INTO product_details VALUES (1,'Home',10,'/vase.png','')");
  jdbc.update("INSERT INTO wallet_entries(user_id,actor_id,request_token,amount,balance_after,note) VALUES (1,1,?,100,100,'Test credit')",UUID.randomUUID().toString());
 }
 String buy(String method){String id=orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(1L,3),"COLLECTION","",method);orders.advance(id,"PREPARING");orders.advance(id,"READY");orders.advance(id,"COMPLETED");return id;}
 String request(String order,int q){return returns.create(order,"a@test.com",1,q,"Damaged",UUID.randomUUID().toString(),null);}
 void receive(String id){returns.process(id,"staff@test.com","APPROVED","Return approved",false,false,false);returns.process(id,"staff@test.com","RECEIVED","Inspected",false,false,false);}
 @Test void partialRefundStockAndDuplicateCompletion() {
  String order=buy("WALLET"),id=request(order,1);receive(id);returns.process(id,"staff@test.com","COMPLETED","Refund approved",true,true,false);returns.process(id,"staff@test.com","COMPLETED","Retry",true,true,false);
  assertEquals(new BigDecimal("75.00"),wallet.balance(1));assertEquals(8,jdbc.queryForObject("SELECT stock_quantity FROM product_details WHERE product_id=1",Integer.class));assertEquals(4,returns.events(id).size());assertEquals(order,wallet.history(1).get(0).orderId());assertEquals("REFUND",wallet.history(1).get(0).kind());
  String second=request(order,2);receive(second);returns.process(second,"staff@test.com","COMPLETED","Not resalable, refund",false,true,false);assertEquals(new BigDecimal("100.00"),wallet.balance(1));assertThrows(IllegalArgumentException.class,()->request(order,1));
 }
 @Test void reservationRejectionAndIdempotentSubmit(){String o=buy("WALLET"),token=UUID.randomUUID().toString();String id=returns.create(o,"a@test.com",1,3,"wrong",token,null);assertEquals(id,returns.create(o,"a@test.com",1,3,"wrong",token,null));assertThrows(IllegalArgumentException.class,()->request(o,1));returns.process(id,"staff@test.com","REJECTED","Ineligible",false,false,false);assertNotNull(request(o,3));}
 @Test void offlineRefundNeverCreditsWallet(){String id=request(buy("OFFLINE"),2);receive(id);assertThrows(IllegalArgumentException.class,()->returns.process(id,"staff@test.com","COMPLETED","Refund",true,true,false));assertEquals("RECEIVED",returns.get(id,"a@test.com",false).status());returns.process(id,"staff@test.com","COMPLETED","Cash refunded",true,true,true);assertEquals(new BigDecimal("100.00"),wallet.balance(1));assertEquals("OFFLINE",returns.get(id,"a@test.com",false).method());}
 @Test void invalidStateAndForeignCustomerCannotMutate(){String o=buy("WALLET"),id=request(o,1);assertThrows(IllegalArgumentException.class,()->returns.process(id,"staff@test.com","COMPLETED","skip",true,true,false));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->returns.process(id,"a@test.com","APPROVED","bad",false,false,false));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->returns.create(o,"b@test.com",1,1,"foreign",UUID.randomUUID().toString(),null));}
 @Test void concurrentCompletionRefundsOnce()throws Exception {String id=request(buy("WALLET"),1);receive(id);var pool=Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);Callable<Void> action=()->{gate.await();returns.process(id,"staff@test.com","COMPLETED","Done",true,true,false);return null;};var a=pool.submit(action);var b=pool.submit(action);gate.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);assertEquals(new BigDecimal("75.00"),wallet.balance(1));assertEquals(4,returns.events(id).size());}finally{pool.shutdownNow();}}
 @Test void linkedReturnIsPrivateAndCanBeSentWithoutText(){String id=request(buy("WALLET"),1);var viewer=support.viewer("a@test.com");String thread=support.send(viewer,null,"",UUID.randomUUID().toString(),null,null,id);assertEquals(id,support.history(viewer,thread,0).messages().get(0).returnId());assertThrows(IllegalArgumentException.class,()->support.send(support.viewer("b@test.com"),null,"",UUID.randomUUID().toString(),null,null,id));}
 @Test void workspaceFiltersCountsAndPriority(){String done=buy("WALLET");String pending=orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(1L,1),"COLLECTION","","OFFLINE");var all=workspace.search("Alice","ALL","","",0);assertEquals(2,all.counts().get("ALL"));assertEquals(pending,all.rows().get(0).id());assertEquals(done,workspace.search(done.substring(0,8),"COMPLETED","","",0).rows().get(0).id());assertTrue(workspace.search("Bob","ALL","","",0).rows().isEmpty());assertThrows(IllegalArgumentException.class,()->workspace.search("","ALL","2026-10-22","2026-10-01",0));}
 @Test void discountedVariantRefundsPreserveCentsAndOriginalPrice() {
  jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES ('old',1,?,'COMPLETED','COLLECTION','',250)",UUID.randomUUID().toString());
  jdbc.update("INSERT INTO product_variants(id,product_id,size_label,color_label,stock_quantity) VALUES (9,1,'M','Green',10)");
  String o=orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(-9L,3),"COLLECTION","","WALLET");orders.advance(o,"PREPARING");orders.advance(o,"READY");orders.advance(o,"COMPLETED");
  jdbc.update("UPDATE products SET price=90 WHERE id=1");
  for(int n=0;n<3;n++){String id=returns.create(o,"a@test.com",-9,1,"Defective",UUID.randomUUID().toString(),null);receive(id);returns.process(id,"staff@test.com","COMPLETED","Refund",true,true,false);}
  assertEquals(new BigDecimal("100.00"),wallet.balance(1));assertEquals(new BigDecimal("35.63"),returns.refunded(o));assertEquals(10,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=9",Integer.class));assertEquals(10,jdbc.queryForObject("SELECT stock_quantity FROM product_details WHERE product_id=1",Integer.class));
 }
 @Test void completionFailureRollsBackRefundAndStock() {
  String id=request(buy("WALLET"),1);receive(id);jdbc.execute("ALTER TABLE return_events ADD CONSTRAINT simulated_failure CHECK(status<>'COMPLETED')");
  assertThrows(org.springframework.dao.DataAccessException.class,()->returns.process(id,"staff@test.com","COMPLETED","Refund",true,true,false));assertEquals(new BigDecimal("62.50"),wallet.balance(1));assertEquals("RECEIVED",returns.get(id,"a@test.com",false).status());assertEquals(7,jdbc.queryForObject("SELECT stock_quantity FROM product_details WHERE product_id=1",Integer.class));
 }
 @Test void uploadedEvidenceAndEveryStageRender()throws Exception {
  String o=buy("WALLET");var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  var photo=new org.springframework.mock.web.MockMultipartFile("image","evidence.png","image/png",bytes.toByteArray());
  mvc.perform(multipart("/orders/"+o+"/return").file(photo).with(user("a@test.com")).with(csrf()).param("itemKey","1").param("quantity","1").param("reason","Damaged").param("token",UUID.randomUUID().toString())).andExpect(status().is3xxRedirection());
  String id=returns.list("a@test.com",false).get(0).id();mvc.perform(get("/orders/returns/"+id+"/image").with(user("b@test.com"))).andExpect(status().isNotFound());mvc.perform(get("/orders/returns/"+id+"/image").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  for(String state:List.of("APPROVED","RECEIVED","COMPLETED")){returns.process(id,"staff@test.com",state,"Checked",true,true,false);mvc.perform(get("/staff/returns/"+id+"?lang=zh").with(user("staff@test.com").roles("STAFF"))).andExpect(status().isOk());}
 }
 @Test void pagesAndAccessControl()throws Exception {
  String o=buy("WALLET"),id=request(o,1);
  if(System.getProperty("odyssey.preview")!=null){
   Path dir=Path.of(System.getProperty("odyssey.preview"));Files.createDirectories(dir);
   for(String[] view:new String[][]{{"orders","/staff/orders"},{"returns","/staff/returns"},{"detail","/staff/returns/"+id}}){
    String html=mvc.perform(get(view[1]+"?lang=zh").with(user("staff@test.com").roles("STAFF"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    Files.writeString(dir.resolve(view[0]+".html"),html);
   }
  }
  for(String path:List.of("/staff/orders","/staff/returns","/staff/returns/"+id))mvc.perform(get(path+"?lang=zh").with(user("staff@test.com").roles("STAFF"))).andExpect(status().isOk());
  for(String path:List.of("/orders/returns","/orders/returns/"+id,"/orders/"+o+"/return"))mvc.perform(get(path+"?lang=zh").with(user("a@test.com").roles("CUSTOMER"))).andExpect(status().isOk());
  mvc.perform(get("/orders/returns/"+id).with(user("b@test.com"))).andExpect(status().isNotFound());mvc.perform(get("/staff/returns").with(user("a@test.com").roles("CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(post("/staff/returns/"+id).with(user("staff@test.com").roles("STAFF")).param("next","APPROVED").param("note","OK")).andExpect(status().isForbidden());
  mvc.perform(get("/support?lang=zh").with(user("a@test.com").roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(containsString("关联售后申请")));
 }
}
