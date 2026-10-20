package com.odyssey.retailshop;

import com.odyssey.retailshop.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AuthTests {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserRepository users;
    @Test void registrationHashesPasswordAndNormalizesEmail() throws Exception {
        when(users.available()).thenReturn(true);
        mvc.perform(post("/register").with(csrf()).param("name","Alice").param("email","ALICE@example.com")
            .param("password","a-long-test-passphrase").param("confirmPassword","a-long-test-passphrase").param("role","ADMIN"))
            .andExpect(redirectedUrl("/login?registered"));
        var hash = ArgumentCaptor.forClass(String.class);
        verify(users).create(eq("Alice"),eq("alice@example.com"),hash.capture());
        assertNotEquals("a-long-test-passphrase",hash.getValue());
        assertTrue(encoder.matches("a-long-test-passphrase",hash.getValue()));
    }
    @Test void invalidRegistrationAndDuplicateAreHandledWithoutEchoingPasswords() throws Exception {
        when(users.available()).thenReturn(true);
        mvc.perform(post("/register").with(csrf()).param("name","Alice").param("email","bad")
            .param("password","private-test-password").param("confirmPassword","different"))
            .andExpect(status().isOk()).andExpect(model().attribute("registrationError","validation"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("private-test-password"))));
        verify(users,never()).create(anyString(),anyString(),anyString());
        doThrow(new DuplicateKeyException("duplicate")).when(users).create(anyString(),anyString(),anyString());
        mvc.perform(post("/register").with(csrf()).param("name","Alice").param("email","alice@example.com")
            .param("password","a-long-passphrase").param("confirmPassword","a-long-passphrase"))
            .andExpect(model().attribute("registrationError","duplicate"));
    }
    @Test void loginAccountLogoutAndSessionRotation() throws Exception {
        when(users.available()).thenReturn(true);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(new UserRepository.Account(1,"Alice","alice@example.com",encoder.encode("test-passphrase"),"CUSTOMER")));
        when(users.findByLogin("alice@example.com")).thenAnswer(invocation -> users.findByEmail("alice@example.com"));
        mvc.perform(get("/account")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").with(csrf()).param("email","alice@example.com").param("password","wrong"))
            .andExpect(redirectedUrl("/login?error"));
        MockHttpSession session = new MockHttpSession(); String oldId=session.getId();
        mvc.perform(post("/login").session(session).with(csrf()).param("email","ALICE@example.com").param("password","test-passphrase"))
            .andExpect(redirectedUrl("/account"));
        assertNotEquals(oldId,session.getId());
        mvc.perform(get("/account").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("Alice")));
        mvc.perform(get("/admin/test").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid());
    }
    @Test void csrfLanguagePersistenceAndDemoMode() throws Exception {
        mvc.perform(post("/register").param("name","Alice")).andExpect(status().isForbidden());
        mvc.perform(get("/register")).andExpect(content().string(containsString("Accounts require the MySQL")));
        mvc.perform(get("/login?lang=zh")).andExpect(cookie().value("odyssey_language","zh"))
            .andExpect(cookie().maxAge("odyssey_language",15552000)).andExpect(cookie().httpOnly("odyssey_language",true))
            .andExpect(content().string(containsString("lang=\"zh-CN\"")));
        mvc.perform(get("/register").cookie(new Cookie("odyssey_language","zh")))
            .andExpect(content().string(containsString("lang=\"zh-CN\"")));
        mvc.perform(get("/login?lang=invalid")).andExpect(cookie().doesNotExist("odyssey_language"));
    }

    @Test void accountCenterKeepsRoleSpecificServicesTogether() throws Exception {
        for (String role : new String[]{"CUSTOMER", "STAFF", "ADMIN"}) {
            when(users.findByEmail("center@test.com")).thenReturn(Optional.of(
                new UserRepository.Account(100,"Center","center@test.com","unused",role)));
            var result = mvc.perform(get("/account?lang=zh")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("center@test.com").roles(role)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("账号与偏好")))
                .andExpect(content().string(containsString("href=\"/orders\"")))
                .andExpect(content().string(containsString("href=\"/membership\"")))
                .andExpect(content().string(containsString("data-new-account-window")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            if(role.equals("ADMIN")) {
                assertFalse(result.contains("href=\"/staff/support\""));
                assertFalse(result.contains("href=\"/support\""));
                assertTrue(result.contains("href=\"/admin/users\""));
            } else assertTrue(result.contains("href=\""+(role.equals("CUSTOMER") ? "/support" : "/staff/support")+"\""));
            assertEquals(role.equals("ADMIN"),result.contains("href=\"/admin\""));
            String header=result.substring(result.indexOf("<header"),result.indexOf("</header>"));
            assertFalse(header.contains("href=\"/orders\""));
            assertFalse(header.contains("href=\"/membership\""));
            assertFalse(header.contains("data-new-account-window"));
        }
    }
}
