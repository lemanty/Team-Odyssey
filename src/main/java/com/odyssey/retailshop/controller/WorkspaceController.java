package com.odyssey.retailshop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Protected landing pages; operational modules will be implemented separately. */
@Controller
public class WorkspaceController {
    private final com.odyssey.retailshop.service.ProductManagementService products;
    private final com.odyssey.retailshop.service.OrderWorkspaceService orders;
    private final com.odyssey.retailshop.service.ReturnService returns;
    public WorkspaceController(com.odyssey.retailshop.service.ProductManagementService products, com.odyssey.retailshop.service.OrderWorkspaceService orders, com.odyssey.retailshop.service.ReturnService returns) { this.products=products; this.orders=orders; this.returns=returns; }
    @GetMapping("/staff")
    public String staff(Model model, org.springframework.security.core.Authentication authentication) {
        if(authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"))) return "redirect:/admin";
        model.addAttribute("administrator", false);
        return "workspace";
    }
    @GetMapping("/admin")
    public String admin(@org.springframework.web.bind.annotation.RequestParam(defaultValue="overview") String section, Model model) {
        model.addAttribute("adminSection",java.util.List.of("new","inventory").contains(section)?section:"overview");
        model.addAttribute("administrator", true);
        if (!java.util.List.of("new","inventory").contains(section) && products.available()) {
            var items=products.list("","all");
            model.addAttribute("productCount",items.size());
            model.addAttribute("lowStock",items.stream().filter(p->p.lowOptions()>0).toList());
            var counts=orders.search("","ALL","","",0).counts();
            model.addAttribute("pendingCount",counts.get("PENDING")+counts.get("PREPARING")+counts.get("READY"));
            model.addAttribute("returnCount",returns.list("",true).stream().filter(q->!java.util.List.of("COMPLETED","REJECTED").contains(q.status())).count());
        }
        model.addAttribute("available",products.available());
        model.addAttribute("form",new com.odyssey.retailshop.service.ProductEditForm());
        return "admin-workspace";
    }
}
