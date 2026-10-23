package com.odyssey.retailshop;

import com.odyssey.retailshop.service.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:coupons;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql") @AutoConfigureMockMvc
class CouponTests {
 @Autowired CouponService coupons;@Autowired OrderService orders;@Autowired WalletService wallet;@Autowired MembershipService membership;@Autowired ReturnService returns;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
 @BeforeEach void setup()throws Exception {
  jdbc.execute("DROP ALL OBJECTS");
  for(String file:List.of("001-products.sql","002-product-details.sql","003-users.sql","005-orders.sql","006-account-cart.sql","009-membership.sql","010-variants-addresses.sql","011-wallet.sql","013-product-management.sql","014-wallet-checkout.sql","015_returns.sql","017-coupons.sql","018-product-reviews.sql")){
   String sql=Files.readString(Path.of("src/main/resources/db",file)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("(?m)^--.*$","");for(String st:sql.split(";"))if(!st.isBlank())jdbc.execute(st);
  }
  jdbc.update("INSERT INTO users(id,display_name,email,password_hash,role) VALUES (1,'Alice','a@test.com','unused','CUSTOMER'),(2,'Bob','b@test.com','unused','CUSTOMER'),(3,'Employee','staff@test.com','unused','STAFF')");
  jdbc.update("INSERT INTO products(id,name,category,description,price) VALUES (1,'Vase','Household','Ceramic',100)");jdbc.update("INSERT INTO product_details VALUES (1,'Home',10,'/vase.png','')");
  jdbc.update("INSERT INTO wallet_entries(user_id,actor_id,request_token,amount,balance_after,note) VALUES (1,1,?,1000,1000,'Test funds')",UUID.randomUUID().toString());
 }
 void coupon(String code,String kind,String value,String minimum,boolean stack){jdbc.update("INSERT INTO coupons(code,title,kind,value_amount,minimum_total,starts_at,ends_at,stack_membership,created_by) VALUES (?,? ,?,?,?,?,?,?,3)",code,"Welcome offer",kind,new BigDecimal(value),new BigDecimal(minimum),LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1),stack);}
 String buy(String code){return orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(1L,1),"COLLECTION","","WALLET",code);}
 void silver(){jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES ('previous',1,?,'COMPLETED','COLLECTION','',250)",UUID.randomUUID().toString());}
 @Test void stackableFixedAndNonStackingPercentageUseCorrectBase(){
  silver();coupon("STACK","FIXED","10","100",true);String id=buy("stack");assertEquals(new BigDecimal("85.00"),orders.get(id,"a@test.com",false).total());assertEquals(new BigDecimal("5.00"),membership.snapshot(id,new BigDecimal("85")).discount());assertEquals(new BigDecimal("10.00"),coupons.snapshot(id).discount());assertEquals(new BigDecimal("915.00"),wallet.balance(1));
  coupon("ONLY","PERCENT","20","0",false);String other=buy("ONLY");assertEquals(new BigDecimal("80.00"),orders.get(other,"a@test.com",false).total());assertEquals(new BigDecimal("0.00"),membership.snapshot(other,new BigDecimal("80")).discount());
 }
 @Test void stackablePercentageAndDiscountCap(){silver();coupon("TEN","PERCENT","10","0",true);assertEquals(new BigDecimal("85.50"),orders.get(buy("TEN"),"a@test.com",false).total());coupon("FREE","FIXED","200","0",true);assertEquals(new BigDecimal("0.00"),orders.get(buy("FREE"),"a@test.com",false).total());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries",Integer.class));}
 @Test void expiredFutureDisabledAndMinimumFailWithoutChargeOrStockChange(){
  coupon("MIN","FIXED","10","101",false);assertThrows(IllegalArgumentException.class,()->buy("MIN"));
  coupon("OLD","FIXED","10","0",false);jdbc.update("UPDATE coupons SET starts_at=?,ends_at=? WHERE code='OLD'",LocalDateTime.now().minusDays(3),LocalDateTime.now().minusDays(1));assertThrows(IllegalArgumentException.class,()->buy("OLD"));
  coupon("LATER","FIXED","10","0",false);jdbc.update("UPDATE coupons SET starts_at=?,ends_at=? WHERE code='LATER'",LocalDateTime.now().plusDays(2),LocalDateTime.now().plusDays(3));assertThrows(IllegalArgumentException.class,()->buy("LATER"));
  coupon("OFF","FIXED","10","0",false);jdbc.update("UPDATE coupons SET enabled=FALSE WHERE code='OFF'");assertThrows(IllegalArgumentException.class,()->buy("OFF"));assertThrows(IllegalArgumentException.class,()->buy("UNKNOWN"));
  assertEquals(new BigDecimal("1000.00"),wallet.balance(1));assertEquals(10,jdbc.queryForObject("SELECT stock_quantity FROM product_details WHERE product_id=1",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM coupon_uses",Integer.class));
 }
 @Test void repeatCheckoutIsIdempotentCancellationRestoresUseAndSnapshot(){
  coupon("ONCE","FIXED","10","0",true);String token=UUID.randomUUID().toString();String id=orders.place("a@test.com",token,Map.of(1L,1),"COLLECTION","","WALLET","ONCE");assertEquals(id,orders.place("a@test.com",token,Map.of(1L,1),"COLLECTION","","WALLET","ONCE"));assertThrows(IllegalArgumentException.class,()->buy("ONCE"));
  orders.cancel(id,"a@test.com");assertEquals(new BigDecimal("1000.00"),wallet.balance(1));assertNotNull(coupons.snapshot(id));assertNotNull(buy("ONCE"));
 }
 @Test void failedPaymentRetainsCouponAndCart(){coupon("PAY","FIXED","10","0",false);jdbc.update("DELETE FROM wallet_entries");jdbc.update("INSERT INTO cart_items VALUES (1,1,1)");assertThrows(IllegalArgumentException.class,()->orders.placeFromCart("a@test.com",UUID.randomUUID().toString(),"COLLECTION","","WALLET","PAY"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM coupon_uses",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class));}
 @Test void concurrentOrdersCannotUseSameCouponTwice()throws Exception {coupon("RACE","FIXED","10","0",false);var pool=Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);Callable<Boolean> action=()->{gate.await();try{buy("RACE");return true;}catch(IllegalArgumentException e){return false;}};var a=pool.submit(action);var b=pool.submit(action);gate.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(new BigDecimal("910.00"),wallet.balance(1));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM coupon_uses",Integer.class));}finally{pool.shutdownNow();}}
 @Test void aftersalesRefundOnlyOriginalDiscountedTotalAndDoesNotRestoreCoupon(){coupon("RETURN","FIXED","25","0",false);String o=buy("RETURN");orders.advance(o,"PREPARING");orders.advance(o,"READY");orders.advance(o,"COMPLETED");String r=returns.create(o,"a@test.com",1,1,"Broken",UUID.randomUUID().toString(),null);returns.process(r,"staff@test.com","APPROVED","OK",false,false,false);returns.process(r,"staff@test.com","RECEIVED","Checked",false,false,false);returns.process(r,"staff@test.com","COMPLETED","Refund",true,true,false);assertEquals(new BigDecimal("75.00"),returns.refunded(o));assertEquals(new BigDecimal("1000.00"),wallet.balance(1));assertThrows(IllegalArgumentException.class,()->buy("RETURN"));}
 @Test void managementAuthorizationValidationAndRendering()throws Exception {
  mvc.perform(get("/staff/coupons").with(user("a@test.com"))).andExpect(status().isForbidden());
  mvc.perform(post("/staff/coupons").with(user("staff@test.com").roles("STAFF"))).andExpect(status().isForbidden());
  var request=post("/staff/coupons").with(user("staff@test.com").roles("STAFF")).with(csrf()).param("code","HELLO").param("title","欢迎优惠").param("kind","PERCENT").param("value","10").param("minimum","100").param("starts",LocalDateTime.now().minusDays(1).toString()).param("ends",LocalDateTime.now().plusDays(1).toString()).param("stack","true");
  mvc.perform(request).andExpect(redirectedUrl("/staff/coupons")).andExpect(flash().attribute("couponMessage",containsString("已创建")));
  mvc.perform(request).andExpect(flash().attribute("couponError",containsString("已存在")));
  mvc.perform(get("/staff/coupons?lang=zh").with(user("staff@test.com").roles("STAFF"))).andExpect(status().isOk()).andExpect(content().string(containsString("欢迎优惠")));
  mvc.perform(get("/account/coupons?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("可叠加会员优惠")));
  if(System.getProperty("odyssey.preview")!=null){
   Path dir=Path.of(System.getProperty("odyssey.preview"));Files.createDirectories(dir);
   for(String[] view:new String[][]{{"manage","/staff/coupons"},{"coupons","/account/coupons"}}){String html=mvc.perform(get(view[1]+"?lang=zh").with(user("staff@test.com").roles("STAFF"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);Files.writeString(dir.resolve(view[0]+".html"),html);}
  }
  long id=coupons.all().get(0).id();mvc.perform(post("/staff/coupons/"+id+"/status").with(user("staff@test.com").roles("STAFF")).with(csrf()).param("enabled","false")).andExpect(status().is3xxRedirection());assertFalse(coupons.all().get(0).enabled());
 }
 @Test void invalidRulesAreRejectedAndOtherCustomersCanUseSameOffer()throws Exception {
  mvc.perform(post("/staff/coupons").with(user("staff@test.com").roles("STAFF")).with(csrf()).param("code","INVALID").param("title","Bad").param("kind","PERCENT").param("value","101").param("minimum","0").param("starts",LocalDateTime.now().minusDays(1).toString()).param("ends",LocalDateTime.now().plusDays(1).toString())).andExpect(flash().attribute("couponError",containsString("百分比")));assertTrue(coupons.all().isEmpty());
  coupon("SHARED","FIXED","10","0",false);buy("SHARED");String bob=orders.place("b@test.com",UUID.randomUUID().toString(),Map.of(1L,1),"COLLECTION","","OFFLINE","SHARED");assertEquals(new BigDecimal("90.00"),orders.get(bob,"b@test.com",false).total());
 }
 @Test void previewKeepsAddressAndCheckoutChargesCoupon()throws Exception {
  coupon("WEB","FIXED","10","0",false);jdbc.update("INSERT INTO cart_items VALUES (1,1,1)");var session=new MockHttpSession();String token=UUID.randomUUID().toString();session.setAttribute("checkoutToken",token);
  var preview=mvc.perform(post("/checkout/coupon").session(session).with(user("a@test.com")).with(csrf()).param("couponCode","WEB").param("address","My road").param("fulfillment","DELIVERY").param("paymentMethod","WALLET")).andExpect(redirectedUrl("/checkout")).andExpect(flash().attribute("checkoutAddress","My road")).andReturn();
  mvc.perform(get("/checkout?lang=zh").session(session).with(user("a@test.com")).flashAttrs(preview.getFlashMap())).andExpect(status().isOk()).andExpect(content().string(containsString("My road"))).andExpect(content().string(containsString("90.00")));
  mvc.perform(post("/orders").session(session).with(user("a@test.com")).with(csrf()).param("token",token).param("fulfillment","COLLECTION").param("paymentMethod","WALLET").param("couponCode","WEB")).andExpect(redirectedUrl("/orders"));
  String id=orders.list("a@test.com",false).get(0).id();assertEquals(new BigDecimal("90.00"),orders.get(id,"a@test.com",false).total());jdbc.update("UPDATE coupons SET title='changed',value_amount=99,enabled=FALSE");
  mvc.perform(get("/orders/"+id+"?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("Welcome offer"))).andExpect(content().string(containsString("−€10.00")));
 }
}
