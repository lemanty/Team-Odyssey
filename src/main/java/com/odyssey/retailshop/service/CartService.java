package com.odyssey.retailshop.service;

import com.odyssey.retailshop.model.Product;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.io.Serializable;
import java.util.*;
import org.springframework.stereotype.Service;

/** Session cart stores IDs and quantities only; prices always come from the catalogue. */
@Service
public class CartService {
    private final ProductService products;
    public CartService(ProductService products) { this.products = products; }

    private static class State implements Serializable {
        final Map<Long, Integer> quantities = new LinkedHashMap<>();
    }
    private State state(HttpSession session) {
        State state = (State) session.getAttribute("cart");
        if (state == null) { state = new State(); session.setAttribute("cart", state); }
        return state;
    }
    public int count(HttpSession session) {
        synchronized (session) { return state(session).quantities.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    public void change(HttpSession session, long id, int quantity, boolean add) {
        synchronized (session) {
            if (quantity < 1 || quantity > 999) throw new IllegalArgumentException("Please choose a quantity between 1 and 999.");
            Product product = products.getProduct(id).orElseThrow(() -> new IllegalArgumentException("This product is no longer available."));
            Map<Long,Integer> items = state(session).quantities;
            if (!add && !items.containsKey(id)) throw new IllegalArgumentException("This product is not in your bag.");
            int next = quantity + (add ? items.getOrDefault(id, 0) : 0);
            if (product.getStockQuantity() == null || next > product.getStockQuantity() || next > 999)
                throw new IllegalArgumentException("The requested quantity is not available. Please check the current stock.");
            items.put(id, next);
        }
    }
    public void remove(HttpSession session, long id) {
        synchronized (session) { state(session).quantities.remove(id); }
    }
    public record Line(long id, Product product, int quantity, BigDecimal subtotal, boolean available) {}
    public record View(List<Line> lines, BigDecimal total) {}
    public View view(HttpSession session) {
        synchronized (session) {
            List<Line> lines = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (var item : state(session).quantities.entrySet()) {
                Product p = products.getProduct(item.getKey()).orElse(null);
                BigDecimal subtotal = p == null ? BigDecimal.ZERO : p.getPrice().multiply(BigDecimal.valueOf(item.getValue()));
                boolean available = p != null && p.getStockQuantity() != null && p.getStockQuantity() >= item.getValue();
                lines.add(new Line(item.getKey(), p, item.getValue(), subtotal, available));
                total = total.add(subtotal);
            }
            return new View(lines, total);
        }
    }
}
