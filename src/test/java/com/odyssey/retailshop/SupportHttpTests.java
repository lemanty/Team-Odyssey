package com.odyssey.retailshop;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP multipart submission: MockMvc pre-parses parts before the security filter. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:supporthttp;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver"})
@ActiveProfiles("mysql")
class SupportHttpTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    String base;
    HttpClient client;
    String match(String html,String regex) {
        var m=Pattern.compile(regex).matcher(html); assertTrue(m.find(),regex); return m.group(1).replace("&amp;","&");
    }
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path,String type,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type",type)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void browserMultipartFormPassesCsrfAndInvalidTokenIsRejected() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        for(String file:List.of("001-products.sql","002-product-details.sql","003-users.sql","005-orders.sql","006-account-cart.sql","007-support.sql","008-support-attachments.sql")) {
            String sql=Files.readString(Path.of("src/main/resources/db",file)).replaceAll("(?m)^--.*$","").replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","");
            for(String statement:sql.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO users(display_name,email,password_hash,role) VALUES ('HTTP Customer','http@test.com',?,'CUSTOMER')",encoder.encode("test-password"));
        base="http://localhost:"+port;
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String login=get("/login").body();
        String csrf=match(login,"name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        assertEquals(302,post("/login","application/x-www-form-urlencoded","email=http%40test.com&password=test-password&_csrf="+URLEncoder.encode(csrf,java.nio.charset.StandardCharsets.UTF_8)).statusCode());
        String html=get("/support").body();
        String action=match(html,"class=\"support-composer\"[^>]*action=\"([^\"]+)\"");
        csrf=match(html,"name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        String token=match(html,"name=\"token\"[^>]*value=\"([^\"]+)\"");
        String boundary="OdysseyTestBoundary";
        StringBuilder body=new StringBuilder();
        for(var field:Map.of("body","Hello from browser","token",token,"_csrf",csrf).entrySet()) {
            body.append("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n");
        }
        body.append("--"+boundary+"--\r\n");
        var result=post(action,"multipart/form-data; boundary="+boundary,body.toString());
        assertEquals(302,result.statusCode(),"Browser multipart should redirect after saving");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM support_messages",Integer.class));
        // Exercise the browser's header-based AJAX submission, including safe duplicate retries.
        var ajax=client.send(HttpRequest.newBuilder(URI.create(base+action))
            .header("Accept","application/json").header("X-CSRF-TOKEN",csrf)
            .header("Content-Type","multipart/form-data; boundary="+boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,ajax.statusCode());
        assertTrue(ajax.body().contains("\"redirect\":\"/support\""));
        assertEquals(403,post("/support/messages?_csrf=invalid","multipart/form-data; boundary="+boundary,body.toString()).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM support_messages",Integer.class));
    }
}
