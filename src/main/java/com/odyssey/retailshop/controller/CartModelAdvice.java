package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CartModelAdvice {
    private final CartService cart;
    public CartModelAdvice(CartService cart) { this.cart = cart; }
    @ModelAttribute("cartCount") public int count(HttpSession session) { return cart.count(session); }
    @ModelAttribute("cartToken") public String token(HttpSession session) { return CartController.token(session); }
}
