package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.CartService;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CartController {
    private final CartService cart;
    private final com.odyssey.retailshop.service.VariantService variants;
    public CartController(CartService cart, com.odyssey.retailshop.service.VariantService variants) { this.cart = cart; this.variants=variants; }
    public static String token(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute("cartToken") == null) session.setAttribute("cartToken", UUID.randomUUID().toString());
            return (String) session.getAttribute("cartToken");
        }
    }
    private void verify(HttpSession session, String csrf) {
        if (!token(session).equals(csrf)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    @GetMapping("/cart")
    public String cart(HttpSession session, Model model) {
        model.addAttribute("bag", cart.view(session));
        return "cart";
    }
    @PostMapping("/cart/add")
    public String add(@RequestParam long productId, @RequestParam(defaultValue="1") int quantity,
                      @RequestParam String csrf, @RequestParam(required=false) Long variantId, HttpSession session, RedirectAttributes flash) {
        verify(session, csrf);
        try {
            if(productId<=0) throw new IllegalArgumentException("Invalid product. / 商品无效。");
            if(variantId!=null && (variantId<=0 || variants.find(variantId).filter(v->v.productId()==productId).isEmpty())) throw new IllegalArgumentException("Invalid product option. / 商品规格不匹配。");
            cart.change(session, variantId==null?productId:-variantId, quantity, true); flash.addFlashAttribute("cartMessage", "Added to your bag."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("cartError", e.getMessage()); }
        return "redirect:/cart";
    }
    @PostMapping("/cart/update")
    public String update(@RequestParam long productId, @RequestParam int quantity,
                         @RequestParam String csrf, HttpSession session, RedirectAttributes flash) {
        verify(session, csrf);
        try { cart.change(session, productId, quantity, false); flash.addFlashAttribute("cartMessage", "Quantity updated."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("cartError", e.getMessage()); }
        return "redirect:/cart";
    }
    @PostMapping("/cart/remove")
    public String remove(@RequestParam long productId, @RequestParam String csrf, HttpSession session, RedirectAttributes flash) {
        verify(session, csrf); cart.remove(session, productId);
        flash.addFlashAttribute("cartMessage", "Removed from your bag.");
        return "redirect:/cart";
    }
}
