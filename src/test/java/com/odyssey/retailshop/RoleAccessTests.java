package com.odyssey.retailshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class RoleAccessTests {
    @Autowired MockMvc mvc;
    @Test void guestMustLogin() throws Exception {
        for (String path : new String[]{"/staff", "/admin"}) mvc.perform(get(path)).andExpect(status().is3xxRedirection());
    }
    @Test void customerCannotEnterEitherWorkspace() throws Exception {
        for (String path : new String[]{"/staff", "/admin", "/staff/orders", "/admin/users"})
            mvc.perform(get(path).with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
    }
    @Test void staffCanEnterStaffOnly() throws Exception {
        mvc.perform(get("/staff").with(user("staff").roles("STAFF"))).andExpect(status().isOk()).andExpect(view().name("workspace"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"admin-shell\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/staff/support\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/staff/products\""))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/admin/users\""))));
        mvc.perform(get("/admin").with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
    }
    @Test void administratorUsesDedicatedConsole() throws Exception {
        mvc.perform(get("/staff").with(user("admin").roles("ADMIN"))).andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(view().name("admin-workspace"));
    }
}
