package com.odyssey.retailshop;

import com.odyssey.retailshop.service.WalletService;
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
import org.springframework.security.test.context.support.WithMockUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:wallet;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql") @AutoConfigureMockMvc
class WalletTests {
 @Autowired WalletService wallets; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
 @BeforeEach void setup() throws Exception {
  jdbc.execute("DROP ALL OBJECTS");
  for(String name:List.of("001-products.sql","002-product-details.sql","003-users.sql","005-orders.sql","006-account-cart.sql","009-membership.sql","010-variants-addresses.sql","011-wallet.sql")){
   String sql=Files.readString(Path.of("src/main/resources/db",name)).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("(?m)^--.*$","");
   for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
  }
  jdbc.update("INSERT INTO users(id,display_name,email,password_hash,role) VALUES (1,'Admin','admin@test.com','unused','ADMIN'),(2,'Alice','a@test.com','unused','CUSTOMER'),(3,'Bob','b@test.com','unused','CUSTOMER')");
 }
 void change(String direction,String amount,String token){wallets.adjust("admin@test.com","a@test.com",direction,new BigDecimal(amount),"Confirmed offline top-up",token);}
 @Test @WithMockUser(username="admin@test.com",roles="ADMIN") void ledgerValidationRetryAndIsolation() throws Exception {
  String token=UUID.randomUUID().toString();change("CREDIT","100",token);change("CREDIT","100",token);
  change("DEBIT","25.50",UUID.randomUUID().toString());
  assertEquals(new BigDecimal("74.50"),wallets.balance(2));assertEquals(2,wallets.history(2).size());assertEquals(0,wallets.balance(3).signum());
  for(String invalid:List.of("0","-1","0.001","1000000"))assertThrows(IllegalArgumentException.class,()->change("CREDIT",invalid,UUID.randomUUID().toString()));
  assertThrows(IllegalArgumentException.class,()->change("DEBIT","75",UUID.randomUUID().toString()));
  assertEquals(new BigDecimal("74.50"),wallets.balance(2));
  mvc.perform(get("/account/wallet?lang=zh").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("74.50")));
  mvc.perform(get("/admin/wallet?email=a@test.com&lang=zh").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("调整余额")));
  mvc.perform(get("/account/wallet").with(user("b@test.com"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Confirmed offline top-up"))));
 }
 @Test void routesCsrfAndRoleBoundaries() throws Exception {
  for(String role:List.of("CUSTOMER","STAFF")) {
   mvc.perform(get("/admin/wallet").with(user("a@test.com").roles(role))).andExpect(status().isForbidden());
   mvc.perform(post("/admin/wallet/adjust").with(user("a@test.com").roles(role)).with(csrf()).param("email","a@test.com").param("direction","CREDIT").param("amount","100").param("note","Test").param("token",UUID.randomUUID().toString())).andExpect(status().isForbidden());
  }
  mvc.perform(post("/admin/wallet/adjust").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isForbidden());
  mvc.perform(get("/account/wallet")).andExpect(status().is3xxRedirection());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->change("CREDIT","100",UUID.randomUUID().toString()));
  mvc.perform(post("/admin/wallet/adjust").with(user("admin@test.com").roles("ADMIN")).with(csrf()).param("email","a@test.com").param("direction","CREDIT").param("amount","20").param("note","Test").param("token",UUID.randomUUID().toString())).andExpect(status().is3xxRedirection());
  assertEquals(new BigDecimal("20.00"),wallets.balance(2));
 }
 @Test @WithMockUser(username="admin@test.com",roles="ADMIN") void concurrentDebitsCannotOverdraw() throws Exception {
  change("CREDIT","100",UUID.randomUUID().toString());
  var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{
   List<Future<Boolean>> results=new ArrayList<>();
   for(int i=0;i<2;i++)results.add(pool.submit(()->{org.springframework.security.core.context.SecurityContextHolder.setContext(context);try{gate.await();change("DEBIT","80",UUID.randomUUID().toString());return true;}catch(IllegalArgumentException e){return false;}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));
   gate.countDown();int successes=0;for(var r:results)if(r.get(15,TimeUnit.SECONDS))successes++;
   assertEquals(1,successes);assertEquals(new BigDecimal("20.00"),wallets.balance(2));
  }finally{pool.shutdownNow();}
 }
}
