package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.SupportService;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.*;

@Controller
public class SupportController {
    private final SupportService support;
    public SupportController(SupportService support) { this.support=support; }
    @GetMapping({"/support","/support/updates","/staff/support","/staff/support/updates"})
    public String page(Principal principal, @RequestParam(required=false) String id,
                       @RequestParam(defaultValue="all") String filter, @RequestParam(defaultValue="0") int page,
                       @RequestParam(defaultValue="0") long before, Model model,
                       jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        var viewer=support.viewer(principal.getName());
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean staffPath=path.startsWith("/staff/");
        if(viewer.staff() && !staffPath) return "redirect:/staff/support";
        if(!viewer.staff() && staffPath) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        page=Math.max(0,Math.min(page,10000)); before=Math.max(0,before);
        filter=Set.of("all","waiting","unread","resolved").contains(filter)?filter:"all";
        var selected=viewer.staff() ? (id==null?null:support.get(viewer,id)) : support.own(viewer);
        // Even customer-supplied IDs must pass ownership checks.
        if(!viewer.staff() && id!=null) selected=support.get(viewer,id);
        model.addAttribute("staffView",viewer.staff()); model.addAttribute("viewer",viewer);
        model.addAttribute("selected",selected); model.addAttribute("filter",filter); model.addAttribute("page",page);
        model.addAttribute("older",before>0); model.addAttribute("sendToken",UUID.randomUUID().toString());
        model.addAttribute("history",selected==null ? new SupportService.History(List.of(),0,0) : support.history(viewer,selected.id(),before));
        model.addAttribute("supportBase",viewer.staff()?"/staff/support":"/support");
        if(viewer.staff()) {
            var rows=support.inbox(viewer,filter,page);
            model.addAttribute("hasNext",rows.size()>30); model.addAttribute("threads",rows.subList(0,Math.min(30,rows.size())));
            model.addAttribute("counts",support.counts(viewer));
        }
        return path.endsWith("/updates")?"support :: workspace":"support";
    }
    @PostMapping({"/support/messages","/staff/support/messages"})
    public String send(Principal principal,@RequestParam(required=false) String id,@RequestParam String body,@RequestParam String token,RedirectAttributes flash) {
        var viewer=support.viewer(principal.getName());
        try {
            String thread=support.send(viewer,id,body,token);
            return viewer.staff()?"redirect:/staff/support?id="+thread:"redirect:/support";
        } catch(IllegalArgumentException e) {
            flash.addFlashAttribute("supportError",e.getMessage()); flash.addFlashAttribute("draft",body);
            return viewer.staff()?"redirect:/staff/support"+(id==null?"":"?id="+id):"redirect:/support";
        }
    }
    @PostMapping({"/support/read","/staff/support/read"})
    public ResponseEntity<Void> read(Principal principal,@RequestParam String id,@RequestParam long through) {
        support.read(support.viewer(principal.getName()),id,through);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/staff/support/resolve")
    public String resolve(Principal principal,@RequestParam String id,@RequestParam long version,RedirectAttributes flash) {
        try { support.resolve(support.viewer(principal.getName()),id,version); }
        catch(IllegalArgumentException e) { flash.addFlashAttribute("supportError",e.getMessage()); }
        return "redirect:/staff/support?id="+id;
    }
}
