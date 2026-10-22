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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:walletcheckout;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql") @AutoConfigureMockMvc
class WalletCheckoutTests {
 @Autowired OrderService orders; @Autowired WalletService wallet; @Autowired OrderPaymentService payments;
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
 @BeforeEach void setup() throws Exception {
  jdbc.execute("DROP ALL OBJECTS");
  for(String file:List.of("001-products.sql","002-product-details.sql","003-users.sql","005-orders.sql","006-account-cart.sql","009-membership.sql","010-variants-addresses.sql","011-wallet.sql","013-product-management.sql","014-wallet-checkout.sql")) {
   String sql=Files.readString(Path.of("src/main/resources/db",file)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("(?m)^--.*$","");
   for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
  }
  jdbc.update("INSERT INTO users(id,display_name,email,password_hash) VALUES (1,'Alice','a@test.com','unused'),(2,'Bob','b@test.com','unused')");
  jdbc.update("INSERT INTO products(id,name,category,description,price) VALUES (1,'Vase','Household','Ceramic',12.50)");
  jdbc.update("INSERT INTO product_details VALUES (1,'Home',10,'/vase.png','')");
 }
 void credit(String amount){jdbc.update("INSERT INTO wallet_entries(user_id,actor_id,request_token,amount,balance_after,note) VALUES (1,1,?,?,?,'Test funds')",UUID.randomUUID().toString(),new BigDecimal(amount),new BigDecimal(amount));}
 String buy(String token){return orders.place("a@test.com",token,Map.of(1L,1),"DELIVERY","Road","WALLET");}
 int stock(){return jdbc.queryForObject("SELECT stock_quantity FROM product_details WHERE product_id=1",Integer.class);}
 int entries(){return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries",Integer.class);}
 @Test void chargeRetryAndRefundAreExactlyOnce() {
  credit("100");String token=UUID.randomUUID().toString();String id=buy(token);
  assertEquals(new BigDecimal("87.50"),wallet.balance(1));assertEquals(9,stock());assertEquals(2,entries());
  assertEquals(id,buy(token));assertEquals(2,entries());
  assertEquals("PAID",payments.snapshot(id).status());assertEquals("PAYMENT",wallet.history(1).get(0).kind());assertEquals(id,wallet.history(1).get(0).orderId());
  orders.cancel(id,"a@test.com");assertEquals(new BigDecimal("100.00"),wallet.balance(1));assertEquals(10,stock());assertEquals(3,entries());assertEquals("REFUNDED",payments.snapshot(id).status());
  assertThrows(IllegalArgumentException.class,()->orders.cancel(id,"a@test.com"));assertEquals(3,entries());
  assertEquals(id,buy(token));assertEquals(3,entries());
 }
 @Test void insufficientBalanceRetainsCartAndRollsBackStock() {
  credit("5");jdbc.update("INSERT INTO cart_items VALUES (1,1,2)");
  assertThrows(IllegalArgumentException.class,()->orders.placeFromCart("a@test.com",UUID.randomUUID().toString(),"DELIVERY","Road","WALLET"));
  assertEquals(new BigDecimal("5.00"),wallet.balance(1));assertEquals(10,stock());assertEquals(1,entries());
  assertEquals(2,jdbc.queryForObject("SELECT quantity FROM cart_items WHERE user_id=1",Integer.class));assertTrue(orders.list("a@test.com",false).isEmpty());
 }
 @Test void failureAfterDebitRollsBackEverything() {
  credit("100");jdbc.update("INSERT INTO cart_items VALUES (1,1,1)");
  jdbc.execute("ALTER TABLE order_membership ADD CONSTRAINT simulated_failure CHECK (discount_total<0)");
  assertThrows(org.springframework.dao.DataAccessException.class,()->orders.placeFromCart("a@test.com",UUID.randomUUID().toString(),"COLLECTION","","WALLET"));
  assertEquals(new BigDecimal("100.00"),wallet.balance(1));assertEquals(10,stock());assertEquals(1,entries());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_payments",Integer.class));
 }
 @Test void discountSnapshotIsChargedAndRefundedEvenAfterPriceChange() {
  credit("100");jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES ('previous',1,?,'COMPLETED','COLLECTION','',250)",UUID.randomUUID().toString());
  String id=buy(UUID.randomUUID().toString());assertEquals(new BigDecimal("11.88"),payments.snapshot(id).amount());
  jdbc.update("UPDATE products SET price=99 WHERE id=1");orders.cancel(id,"a@test.com");assertEquals(new BigDecimal("100.00"),wallet.balance(1));assertEquals(new BigDecimal("11.88"),wallet.history(1).get(0).amount());
 }
 @Test void concurrentOrdersCannotOverspendAndConcurrentCancelCannotDoubleRefund() throws Exception {
  credit("15");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   Callable<String> action=()->{start.await();try{return buy(UUID.randomUUID().toString());}catch(IllegalArgumentException e){return null;}};
   var a=pool.submit(action);var b=pool.submit(action);start.countDown();String first=a.get(15,TimeUnit.SECONDS),second=b.get(15,TimeUnit.SECONDS);
   assertTrue((first==null)!=(second==null));assertEquals(new BigDecimal("2.50"),wallet.balance(1));assertEquals(9,stock());
   String id=first==null?second:first;var gate=new CountDownLatch(1);
   Callable<Boolean> cancel=()->{gate.await();try{orders.cancel(id,"a@test.com");return true;}catch(IllegalArgumentException e){return false;}};
   var x=pool.submit(cancel);var y=pool.submit(cancel);gate.countDown();assertNotEquals(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS));
   assertEquals(new BigDecimal("15.00"),wallet.balance(1));assertEquals(3,entries());assertEquals(10,stock());
  }finally{pool.shutdownNow();}
 }
 @Test void cannotRefundOthersOrPreparingOrders() throws Exception {
  credit("100");String id=buy(UUID.randomUUID().toString());
  mvc.perform(post("/orders/"+id+"/cancel").with(user("b@test.com")).with(csrf())).andExpect(status().isNotFound());
  orders.advance(id,"PREPARING");assertThrows(IllegalArgumentException.class,()->orders.cancel(id,"a@test.com"));
  assertEquals(new BigDecimal("87.50"),wallet.balance(1));assertEquals("PAID",payments.snapshot(id).status());
 }
 @Test void offlineLegacyAndZeroTotalDoNotCreateFakeMoney() {
  String id=orders.place("a@test.com",UUID.randomUUID().toString(),Map.of(1L,1),"COLLECTION","","OFFLINE");
  assertEquals("UNPAID",payments.snapshot(id).status());orders.cancel(id,"a@test.com");assertEquals(0,entries());assertEquals("CANCELLED",payments.snapshot(id).status());
  jdbc.update("DELETE FROM order_payments WHERE order_id=?",id);assertEquals("UNRECORDED",payments.snapshot(id).status());
  jdbc.update("UPDATE products SET price=0 WHERE id=1");String free=buy(UUID.randomUUID().toString());assertEquals("PAID",payments.snapshot(free).status());orders.cancel(free,"a@test.com");assertEquals(0,entries());
 }
 @Test void webCheckoutShowsBalancePaymentAndRefundActivity() throws Exception {
  credit("100");jdbc.update("INSERT INTO cart_items VALUES (1,1,1)");var session=new MockHttpSession();String token=UUID.randomUUID().toString();session.setAttribute("checkoutToken",token);
  mvc.perform(get("/checkout?lang=zh").session(session).with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("储值余额支付"))).andExpect(content().string(containsString("100.00")));
  mvc.perform(post("/orders").session(session).with(user("a@test.com")).param("token",token).param("fulfillment","COLLECTION").param("paymentMethod","WALLET")).andExpect(status().isForbidden());
  mvc.perform(post("/orders").session(session).with(user("a@test.com")).with(csrf()).param("token",token).param("fulfillment","COLLECTION").param("paymentMethod","WALLET")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders"));
  String id=orders.list("a@test.com",false).get(0).id();
  mvc.perform(get("/orders/"+id+"?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("已支付")));
  orders.cancel(id,"a@test.com");
  mvc.perform(get("/account/wallet?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("订单退款"))).andExpect(content().string(containsString("/orders/"+id)));
 }
 @Test void insufficientBalanceAndInvalidMethodHttpFeedback() throws Exception {
  jdbc.update("INSERT INTO cart_items VALUES (1,1,1)");var session=new MockHttpSession();String token=UUID.randomUUID().toString();session.setAttribute("checkoutToken",token);
  mvc.perform(get("/checkout?lang=zh").session(session).with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("余额不足")));
  mvc.perform(post("/orders").session(session).with(user("a@test.com")).with(csrf()).param("token",token).param("fulfillment","DELIVERY").param("address","My road").param("paymentMethod","WALLET"))
   .andExpect(redirectedUrl("/checkout")).andExpect(flash().attribute("checkoutAddress","My road")).andExpect(flash().attribute("orderError",containsString("余额不足")));
  assertThrows(IllegalArgumentException.class,()->orders.place("a@test.com",token,Map.of(1L,1),"COLLECTION","","CARD"));assertEquals(10,stock());
 }
}
