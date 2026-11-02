package com.odyssey.retailshop.service;

import com.odyssey.retailshop.model.Product;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.io.Serializable;
import java.util.*;
import org.springframework.stereotype.Service;

/** Signed-in carts persist by account; anonymous carts remain isolated in the session. */
@Service
public class CartService {
    private final ProductService products;
    private final VariantService variants;
    private final com.odyssey.retailshop.repository.AccountCartRepository accounts;
    public CartService(ProductService products, com.odyssey.retailshop.repository.AccountCartRepository accounts, VariantService variants) { this.products = products; this.accounts=accounts; this.variants=variants; }
    private String owner() {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) return null;
        return auth.getName();
    }
    private Map<Long,Integer> read(HttpSession session) {
        String owner=owner();
        if (owner==null) return java.util.Collections.emptyMap();
        if (accounts.available()) return accounts.read(owner);
        return state(session).quantities;
    }
    private void modify(HttpSession session, java.util.function.Consumer<Map<Long,Integer>> action) {
        String owner=owner();
        if (owner==null) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Sign in before changing your bag");
        if (accounts.available()) accounts.modify(owner,action);
        else action.accept(state(session).quantities);
    }

    private static class State implements Serializable {
        final Map<Long, Integer> quantities = new LinkedHashMap<>();
    }
    private State state(HttpSession session) {
        String key = owner()==null ? "cart" : "cart:"+owner();
        State state = (State) session.getAttribute(key);
        if (state == null) { state = new State(); session.setAttribute(key, state); }
        return state;
    }
    public Map<Long,Integer> quantities(HttpSession session) {
        synchronized (session) { return new LinkedHashMap<>(read(session)); }
    }
    public void clear(HttpSession session) {
        synchronized (session) { modify(session, Map::clear); }
    }
    public int count(HttpSession session) {
        synchronized (session) { return read(session).values().stream().mapToInt(Integer::intValue).sum(); }
    }
    public void change(HttpSession session, long id, int quantity, boolean add) {
        synchronized (session) {
            if (quantity < 1 || quantity > 999) throw new IllegalArgumentException("Please choose a quantity between 1 and 999.");
            Product product = products.getProduct(variants.productId(id)).orElseThrow(() -> new IllegalArgumentException("This product is no longer available."));
            var variant = id<0 ? variants.find(-id).orElseThrow() : null;
            if (id>0 && !variants.list(id).isEmpty()) throw new IllegalArgumentException("Please choose size and colour on the product page. / 请在商品详情选择尺码和颜色。");
            modify(session, items -> {
            if (!add && !items.containsKey(id)) throw new IllegalArgumentException("This product is not in your bag.");
            int next = quantity + (add ? items.getOrDefault(id, 0) : 0);
            if (product.getStockQuantity() == null || next > product.getStockQuantity() || (variant!=null && next>variant.stock()) || next > 999)
                throw new IllegalArgumentException("The requested quantity is not available. Please check the current stock.");
            items.put(id, next);
            });
        }
    }
    public void remove(HttpSession session, long id) {
        synchronized (session) { modify(session, items -> items.remove(id)); }
    }
    public record Line(long id, Product product, int quantity, BigDecimal subtotal, boolean available, String size, String color) {}
    public record View(List<Line> lines, BigDecimal total) {}
    public View view(HttpSession session) {
        synchronized (session) {
            List<Line> lines = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (var item : read(session).entrySet()) {
                var variant = item.getKey()<0 ? variants.find(-item.getKey()).orElse(null) : null;
                Product p = products.getProduct(item.getKey()<0 ? (variant==null?0:variant.productId()) : item.getKey()).orElse(null);
                BigDecimal subtotal = p == null ? BigDecimal.ZERO : p.getPrice().multiply(BigDecimal.valueOf(item.getValue()));
                boolean available = p != null && p.getStockQuantity() != null && p.getStockQuantity() >= item.getValue() && (variant!=null ? variant.stock()>=item.getValue() : variants.list(p.getId()).isEmpty());
                lines.add(new Line(item.getKey(), p, item.getValue(), subtotal, available,variant==null?"":variant.size(),variant==null?"":variant.color()));
                total = total.add(subtotal);
            }
            return new View(lines, total);
        }
    }
}
