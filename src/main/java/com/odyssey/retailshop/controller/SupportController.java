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
        var history=selected==null ? new SupportService.History(List.of(),0,0) : support.history(viewer,selected.id(),before);
        model.addAttribute("history",history);
        model.addAttribute("linkedOrderPreviews",selected==null ? Map.of() : support.orderPreviews(viewer,selected.id(),history));
        model.addAttribute("orderChoices",support.orderChoices(viewer,selected==null?null:selected.id()));
        model.addAttribute("returnChoices",support.returnChoices(viewer,selected==null?null:selected.id()));
        model.addAttribute("supportBase",viewer.staff()?"/staff/support":"/support");
        if(viewer.staff()) {
            var rows=support.inbox(viewer,filter,page);
            model.addAttribute("hasNext",rows.size()>30); model.addAttribute("threads",rows.subList(0,Math.min(30,rows.size())));
            model.addAttribute("counts",support.counts(viewer));
        }
        return path.endsWith("/updates")?"support :: workspace":"support";
    }
    @PostMapping({"/support/messages","/staff/support/messages"})
    public Object send(Principal principal,@RequestParam(required=false) String id,@RequestParam(defaultValue="") String body,@RequestParam String token,
                       @RequestParam(required=false) String returnId,@RequestParam(required=false) String orderId,@RequestParam(required=false) org.springframework.web.multipart.MultipartFile image,RedirectAttributes flash,
                       @RequestHeader(value="Accept",defaultValue="") String accept) {
        var viewer=support.viewer(principal.getName());
        try {
            String thread=support.send(viewer,id,body,token,orderId,com.odyssey.retailshop.service.SupportImage.from(image),returnId);
            String destination=viewer.staff()?"/staff/support?id="+thread:"/support";
            if(accept.contains("application/json")) return ResponseEntity.ok(Map.of("redirect",destination));
            return "redirect:"+destination;
        } catch(IllegalArgumentException e) {
            if(accept.contains("application/json")) return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
            flash.addFlashAttribute("supportError",e.getMessage()); flash.addFlashAttribute("draft",body); flash.addFlashAttribute("selectedOrder",orderId);
            return viewer.staff()?"redirect:/staff/support"+(id==null?"":"?id="+id):"redirect:/support";
        }
    }
    @GetMapping("/support/images/{messageId}")
    public ResponseEntity<byte[]> image(Principal principal,@PathVariable long messageId) {
        var image=support.image(support.viewer(principal.getName()),messageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.type()))
            .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition","inline; filename=chat-image").body(image.bytes());
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public Object tooLarge(RedirectAttributes flash,java.security.Principal principal, jakarta.servlet.http.HttpServletRequest request) {
        if(Optional.ofNullable(request.getHeader("Accept")).orElse("").contains("application/json"))
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error","The image is too large (maximum 5 MB). / 图片太大（最大5MB）。"));
        flash.addFlashAttribute("supportError","The image is too large. Choose an image up to 5 MB. / 图片太大，请选择不超过5MB的图片。");
        return support.viewer(principal.getName()).staff()?"redirect:/staff/support":"redirect:/support";
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
