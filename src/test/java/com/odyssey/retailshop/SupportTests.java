package com.odyssey.retailshop;

import com.odyssey.retailshop.service.SupportService;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:support;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql")
@AutoConfigureMockMvc
class SupportTests {
    @Autowired SupportService support;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    SupportService.Viewer customer,staff,otherStaff,other;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        for(String file:List.of("001-products.sql","013-product-management.sql","002-product-details.sql","003-users.sql","005-orders.sql","011-wallet.sql","014-wallet-checkout.sql","009-membership.sql","010-variants-addresses.sql","006-account-cart.sql","007-support.sql","008-support-attachments.sql")) {
            String sql=Files.readString(Path.of("src/main/resources/db",file)).replaceAll("(?m)^--.*$","").replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","");
            for(String statement:sql.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO users(id,display_name,email,password_hash,role) VALUES (1,'Alice','a@test.com','unused','CUSTOMER'),(2,'Bob','b@test.com','unused','CUSTOMER'),(3,'Staff One','s@test.com','unused','STAFF'),(4,'Staff Two','t@test.com','unused','STAFF'),(5,'Admin','admin@test.com','unused','ADMIN')");
        customer=support.viewer("a@test.com"); other=support.viewer("b@test.com");staff=support.viewer("s@test.com");otherStaff=support.viewer("t@test.com");
    }
    String message(SupportService.Viewer sender,String id,String body) { return support.send(sender,id,body,UUID.randomUUID().toString()); }
    @Test void replyQueueReadStateAndResolutionAreDistinct() {
        String id=message(customer,null,"Where is my order?");
        var thread=support.get(staff,id); assertTrue(thread.waiting()); assertTrue(thread.unread());
        support.read(staff,id,thread.latestId());
        assertFalse(support.get(staff,id).unread()); assertTrue(support.get(staff,id).waiting());
        assertTrue(support.get(otherStaff,id).unread());
        assertThrows(IllegalArgumentException.class,()->support.resolve(staff,id,thread.latestId()));
        message(staff,id,"We are preparing it.");
        assertFalse(support.get(staff,id).waiting()); assertTrue(support.get(customer,id).unread());
        long version=support.get(staff,id).latestId(); support.resolve(staff,id,version);
        assertTrue(support.get(customer,id).resolved());
        message(customer,id,"Thank you. Another question.");
        assertFalse(support.get(staff,id).resolved()); assertTrue(support.get(staff,id).waiting());
        assertThrows(IllegalArgumentException.class,()->support.resolve(staff,id,version));
        assertEquals(1,support.counts(staff).waiting());
        assertEquals(1,support.inbox(staff,"waiting",0).size());
    }
    @Test void duplicateSendAndInvalidMessagesDoNotCreateExtraRows() {
        String token=UUID.randomUUID().toString();
        String id=support.send(customer,null,"Hello",token);
        assertEquals(id,support.send(customer,null,"Hello",token));
        assertEquals(1,support.history(customer,id,0).messages().size());
        assertThrows(IllegalArgumentException.class,()->message(customer,id,"  \n "));
        assertThrows(IllegalArgumentException.class,()->message(customer,id,"x".repeat(2001)));
        assertThrows(IllegalArgumentException.class,()->support.send(customer,id,"Hi","forged"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->support.get(other,id));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->message(other,id,"intrusion"));
    }
    @Test void permissionsCsrfEscapingAndBothInterfacesRender() throws Exception {
        String id=message(customer,null,"<script>alert('x')</script>");
        mvc.perform(get("/support")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/staff/support").with(user("a@test.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/support?id="+id).with(user("b@test.com"))).andExpect(status().isNotFound());
        mvc.perform(post("/support/messages").with(user("a@test.com")).param("body","Hi").param("token",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(post("/support/read").with(user("b@test.com")).with(csrf()).param("id",id).param("through","999")).andExpect(status().isNotFound());
        mvc.perform(get("/support?lang=zh").with(user("a@test.com")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("等待客服回复")))
            .andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(not(containsString("<script>alert"))));
        mvc.perform(get("/staff/support?id="+id+"&lang=zh").with(user("s@test.com").roles("STAFF")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("needs-reply"))).andExpect(content().string(containsString("未读")));
        mvc.perform(get("/staff/support/updates?id="+id).with(user("s@test.com").roles("STAFF")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/support").with(user("s@test.com").roles("STAFF"))).andExpect(redirectedUrl("/staff/support"));
        mvc.perform(post("/staff/support/messages").with(user("s@test.com").roles("STAFF")).with(csrf()).param("id",id).param("body","Reply").param("token",UUID.randomUUID().toString())).andExpect(redirectedUrl("/staff/support?id="+id));
        mvc.perform(get("/support/updates").with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("Reply")));
    }
    @Test void firstMessageAndHistoryPagination() throws Exception {
        mvc.perform(get("/support").with(user("b@test.com"))).andExpect(status().isOk()).andExpect(content().string(containsString("Send your first message")));
        String id=message(customer,null,"First");
        for(int i=0;i<51;i++) message(customer,id,"Message "+i);
        var latest=support.history(customer,id,0); assertEquals(50,latest.messages().size()); assertTrue(latest.before()>0);
        var older=support.history(customer,id,latest.before()); assertEquals(2,older.messages().size()); assertEquals(0,older.before());
        support.read(staff,id,older.latestVisible()); assertTrue(support.get(staff,id).unread());
    }
    private org.springframework.mock.web.MockMultipartFile picture() throws Exception {
        var output=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);
        return new org.springframework.mock.web.MockMultipartFile("image","photo.png","image/png",output.toByteArray());
    }
    @Test void imageAndOrderMessagePersistsAndIsPrivate() throws Exception {
        String order=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES (?,1,?,'PENDING','COLLECTION','',25)",order,UUID.randomUUID().toString());
        jdbc.update("INSERT INTO products(id,name,category,description,price) VALUES (1,'Test shirt','Clothing','Sample',25),(2,'Second shirt','Clothing','Sample',10)");
        jdbc.update("INSERT INTO product_details VALUES (1,'MEN',5,'/images/test-shirt.png','/images/test-model.png')");
        jdbc.update("INSERT INTO order_items VALUES (?,1,'Test shirt',25,1),(?,2,'Second shirt',10,2)",order,order);
        String token=UUID.randomUUID().toString();
        mvc.perform(multipart("/support/messages").file(picture()).with(user("a@test.com")).with(csrf()).param("body","").param("token",token).param("orderId",order))
            .andExpect(redirectedUrl("/support"));
        String thread=support.own(customer).id();
        var message=support.history(customer,thread,0).messages().get(0);
        var previews=support.orderPreviews(customer,thread,support.history(customer,thread,0)).get(order);
        assertEquals(2,previews.size()); assertEquals("/images/test-shirt.png",previews.get(0).image());
        assertNull(previews.get(1).image());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->support.orderPreviews(other,thread,support.history(customer,thread,0)));
        mvc.perform(get("/staff/support?id="+thread).with(user("s@test.com").roles("STAFF")))
            .andExpect(content().string(containsString("/images/test-shirt.png")));
        assertTrue(message.image()); assertEquals(order,message.orderId()); assertTrue(support.get(staff,thread).waiting());
        mvc.perform(get("/support/images/"+message.id()).with(user("a@test.com"))).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(get("/support/images/"+message.id()).with(user("s@test.com").roles("STAFF"))).andExpect(status().isOk());
        mvc.perform(get("/support/images/"+message.id()).with(user("b@test.com"))).andExpect(status().isNotFound());
        mvc.perform(get("/support/images/"+message.id())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/support?lang=zh").with(user("a@test.com"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("/support/images/"+message.id())))
            .andExpect(content().string(containsString("/orders/"+order)))
            .andExpect(content().string(containsString("/images/test-shirt.png")))
            .andExpect(content().string(containsString("暂无图片")));
        mvc.perform(multipart("/support/messages").file(picture()).with(user("a@test.com")).with(csrf()).param("body","").param("token",token).param("orderId",order)).andExpect(status().is3xxRedirection());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM support_message_extras",Integer.class));
    }
    @Test void rejectsForeignOrdersAndFakeImagesWithoutSaving() throws Exception {
        String foreign=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES (?,2,?,'PENDING','COLLECTION','',25)",foreign,UUID.randomUUID().toString());
        String thread=message(customer,null,"Hello");
        assertTrue(support.orderChoices(customer,thread).isEmpty());
        assertTrue(support.orderChoices(staff,thread).isEmpty());
        assertThrows(IllegalArgumentException.class,()->support.send(customer,thread,"",UUID.randomUUID().toString(),foreign,null));
        assertThrows(IllegalArgumentException.class,()->support.send(staff,thread,"",UUID.randomUUID().toString(),foreign,null));
        var fake=new org.springframework.mock.web.MockMultipartFile("image","fake.png","image/png","<svg onload=alert(1)>".getBytes());
        mvc.perform(multipart("/support/messages").file(fake).with(user("a@test.com")).with(csrf()).param("body","").param("token",UUID.randomUUID().toString()))
            .andExpect(flash().attributeExists("supportError"));
        mvc.perform(multipart("/support/messages").file(picture()).with(user("a@test.com")).param("token",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertEquals(1,support.history(customer,thread,0).messages().size());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM support_message_extras",Integer.class));
    }
    @Test void concurrentRetriesProduceOneMessage() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);String token=UUID.randomUUID().toString();
        try {
            Callable<String> send=()->{gate.await();return support.send(customer,null,"Concurrent",token);};
            var first=pool.submit(send);var second=pool.submit(send);gate.countDown();
            String id=first.get(15,TimeUnit.SECONDS);assertEquals(id,second.get(15,TimeUnit.SECONDS));
            assertEquals(1,support.history(customer,id,0).messages().size());
        } finally { pool.shutdownNow(); }
    }
}
