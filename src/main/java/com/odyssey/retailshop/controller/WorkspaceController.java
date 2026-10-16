package com.odyssey.retailshop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Protected landing pages; operational modules will be implemented separately. */
@Controller
public class WorkspaceController {
    @GetMapping("/staff")
    public String staff(Model model) {
        model.addAttribute("administrator", false);
        return "workspace";
    }
    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("administrator", true);
        return "workspace";
    }
}
