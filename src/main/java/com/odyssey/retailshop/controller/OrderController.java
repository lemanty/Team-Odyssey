package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.*;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderController {
    private final OrderService orders;
    private final CartService cart;
    public OrderController(OrderService orders, CartService cart) { this.orders=orders; this.cart=cart; }
    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        synchronized(session) {
            if (session.getAttribute("checkoutToken") == null) session.setAttribute("checkoutToken",UUID.randomUUID().toString());
            model.addAttribute("checkoutToken",session.getAttribute("checkoutToken"));
            model.addAttribute("bag",cart.view(session));
        }
        return "checkout";
    }
    @PostMapping("/orders")
    public String place(@RequestParam String token, @RequestParam String fulfillment, @RequestParam(defaultValue="") String address, Principal user, HttpSession session, RedirectAttributes flash) {
        synchronized(session) {
            // Old tokens can retry the existing order, but cannot create a new order.
            String previous=(String)session.getAttribute("lastCheckoutToken");
            if (!token.equals(session.getAttribute("checkoutToken")) && !token.equals(previous)) {
                flash.addFlashAttribute("orderError","Please reopen checkout. / 请重新打开结算页。"); return "redirect:/checkout";
            }
            if (token.equals(previous)) return "redirect:/orders";
            try {
                String id=orders.placeFromCart(user.getName(),token,fulfillment,address);
                session.removeAttribute("checkoutToken");
                session.setAttribute("lastCheckoutToken",token); session.setAttribute("lastCheckoutId",id);
                flash.addFlashAttribute("orderMessage", "Order placed successfully. / 订单提交成功。");
                return "redirect:/orders";
            } catch (IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); return "redirect:/checkout"; }
        }
    }
    @GetMapping("/orders")
    public String mine(Principal user, Model model) { return listing(user,model,false); }
    @GetMapping("/staff/orders")
    public String staff(Principal user, Model model) { return listing(user,model,true); }
    private String listing(Principal user, Model model, boolean staff) {
        model.addAttribute("orderPreviews",orders.previews(user.getName(),staff));
        model.addAttribute("orders",orders.list(user.getName(),staff)); model.addAttribute("staffView",staff); return "orders";
    }
    @GetMapping("/orders/{id}")
    public String detail(@PathVariable String id, Principal user, Model model) { return detail(id,user,model,false); }
    @GetMapping("/staff/orders/{id}")
    public String staffDetail(@PathVariable String id, Principal user, Model model) { return detail(id,user,model,true); }
    private String detail(String id, Principal user, Model model, boolean staff) {
        model.addAttribute("order",orders.get(id,user.getName(),staff));
        model.addAttribute("items",orders.items(id)); model.addAttribute("staffView",staff); return "order-detail";
    }
    @PostMapping("/orders/{id}/edit")
    public String edit(@PathVariable String id, @RequestParam String fulfillment, @RequestParam(defaultValue="") String address, Principal user, RedirectAttributes flash) {
        try { orders.edit(id,user.getName(),fulfillment,address); flash.addFlashAttribute("orderMessage","Order updated. / 订单已更新。"); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable String id, Principal user, RedirectAttributes flash) {
        try { orders.cancel(id,user.getName()); flash.addFlashAttribute("orderMessage","Order cancelled; stock released. / 订单已取消，库存已释放。"); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @PostMapping("/orders/{id}/confirm-receipt")
    public String confirmReceipt(@PathVariable String id,Principal user,RedirectAttributes flash) {
        try { orders.confirmReceipt(id,user.getName()); flash.addFlashAttribute("orderMessage","Receipt confirmed. Order completed. / 已确认收货，订单已完成。"); }
        catch(IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/orders/"+id;
    }
    @PostMapping("/staff/orders/{id}/status")
    public String advance(@PathVariable String id, @RequestParam String next, RedirectAttributes flash) {
        try { orders.advance(id,next); flash.addFlashAttribute("orderMessage","Status updated. / 状态已更新。"); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("orderError",e.getMessage()); }
        return "redirect:/staff/orders/"+id;
    }
}
