package com.odyssey.retailshop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Protected landing pages; operational modules will be implemented separately. */
@Controller
public class WorkspaceController {
    private final com.odyssey.retailshop.service.ProductManagementService products;
    public WorkspaceController(com.odyssey.retailshop.service.ProductManagementService products) { this.products=products; }
    @GetMapping("/staff")
    public String staff(Model model, org.springframework.security.core.Authentication authentication) {
        if(authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"))) return "redirect:/admin";
        model.addAttribute("administrator", false);
        return "workspace";
    }
    @GetMapping("/admin")
    public String admin(@org.springframework.web.bind.annotation.RequestParam(defaultValue="inventory") String section, Model model) {
        model.addAttribute("adminSection",section.equals("new")?"new":"inventory");
        model.addAttribute("administrator", true);
        model.addAttribute("available",products.available());
        model.addAttribute("form",new com.odyssey.retailshop.service.ProductEditForm());
        return "admin-workspace";
    }
}
