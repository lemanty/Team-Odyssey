package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public OrderService(ObjectProvider<JdbcTemplate> jdbc, ObjectProvider<PlatformTransactionManager> manager) {
        this.jdbc = jdbc.getIfAvailable();
        var transactionManager = manager.getIfAvailable();
        this.tx = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }
    public record Order(String id, long userId, String status, String fulfillment, String address, BigDecimal total, LocalDateTime createdAt) {}
    public record Item(long productId, String productName, BigDecimal unitPrice, int quantity) {
        public BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }
    private void ready() {
        if (jdbc == null || tx == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Orders require MySQL mode.");
    }
    private Order row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Order(r.getString("id"),r.getLong("user_id"),r.getString("status"),r.getString("fulfillment"),r.getString("address"),r.getBigDecimal("total"),r.getTimestamp("created_at").toLocalDateTime());
    }
    public List<Order> list(String email, boolean staff) {
        ready();
        return staff ? jdbc.query("SELECT * FROM shop_orders ORDER BY created_at DESC, id", this::row)
            : jdbc.query("SELECT o.* FROM shop_orders o JOIN users u ON u.id=o.user_id WHERE u.email=? ORDER BY o.created_at DESC, o.id", this::row, email);
    }
    public record Preview(long productId, String name, int quantity, BigDecimal unitPrice, String image) {}
    public Map<String,List<Preview>> previews(String email, boolean staff) {
        ready();
        Map<String,List<Preview>> result=new LinkedHashMap<>();
        String sql="SELECT i.*, d.garment_image FROM order_items i JOIN shop_orders o ON o.id=i.order_id JOIN users u ON u.id=o.user_id LEFT JOIN product_details d ON d.product_id=i.product_id"
            + (staff ? "" : " WHERE u.email=?") + " ORDER BY i.order_id,i.product_id";
        jdbc.query(sql, r -> {
            result.computeIfAbsent(r.getString("order_id"), key -> new ArrayList<>()).add(
                new Preview(r.getLong("product_id"),r.getString("product_name"),r.getInt("quantity"),r.getBigDecimal("unit_price"),r.getString("garment_image")));
        }, staff ? new Object[]{} : new Object[]{email});
        return result;
    }
    public Order get(String id, String email, boolean staff) {
        ready();
        var rows = staff ? jdbc.query("SELECT * FROM shop_orders WHERE id=?",this::row,id)
            : jdbc.query("SELECT o.* FROM shop_orders o JOIN users u ON u.id=o.user_id WHERE o.id=? AND u.email=?",this::row,id,email);
        return rows.stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public List<Item> items(String id) {
        return jdbc.query("SELECT * FROM order_items WHERE order_id=? ORDER BY product_id", (r,n) -> new Item(r.getLong("product_id"),r.getString("product_name"),r.getBigDecimal("unit_price"),r.getInt("quantity")), id);
    }
    private String address(String fulfillment, String address) {
        if (!Set.of("DELIVERY","COLLECTION").contains(fulfillment)) throw new IllegalArgumentException("Choose delivery or collection. / 请选择配送或自取。");
        String clean = address == null ? "" : address.strip();
        if (fulfillment.equals("COLLECTION")) return "";
        if (clean.isBlank() || clean.length() > 500) throw new IllegalArgumentException("Enter a delivery address (up to 500 characters). / 请填写配送地址（最多500字）。");
        return clean;
    }
    public String placeFromCart(String email, String token, String fulfillment, String address) {
        ready();
        return tx.execute(status -> {
            Long userId=jdbc.queryForObject("SELECT id FROM users WHERE email=? FOR UPDATE",Long.class,email);
            var existing=jdbc.queryForList("SELECT id FROM shop_orders WHERE user_id=? AND checkout_token=?",String.class,userId,token);
            if (!existing.isEmpty()) return existing.get(0);
            Map<Long,Integer> cart=new LinkedHashMap<>();
            jdbc.query("SELECT product_id,quantity FROM cart_items WHERE user_id=? ORDER BY product_id",r -> { cart.put(r.getLong(1),r.getInt(2)); },userId);
            String id=place(email,token,cart,fulfillment,address);
            jdbc.update("DELETE FROM cart_items WHERE user_id=?",userId);
            return id;
        });
    }
    public String place(String email, String token, Map<Long,Integer> cart, String fulfillment, String address) {
        ready();
        String destination = address(fulfillment,address);
        try { UUID.fromString(token); } catch (RuntimeException e) { throw new IllegalArgumentException("Please reopen checkout. / 请重新打开结算页。"); }
        return tx.execute(s -> {
            // Serialize this customer's retries; a repeated checkout token returns the original order.
            Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=? FOR UPDATE",Long.class,email);
            var existing = jdbc.queryForList("SELECT id FROM shop_orders WHERE user_id=? AND checkout_token=?",String.class,userId,token);
            if (!existing.isEmpty()) return existing.get(0);
            if (cart.isEmpty()) throw new IllegalArgumentException("Your bag is empty. / 购物车为空。");
            List<Item> lines = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            // Consistent lock order avoids deadlocks between carts containing the same products.
            for (var entry : new TreeMap<>(cart).entrySet()) {
                int quantity = entry.getValue();
                if (quantity < 1 || quantity > 999) throw new IllegalArgumentException("Invalid quantity. / 数量无效。");
                var product = jdbc.query("SELECT name,price FROM products WHERE id=? FOR UPDATE", (r,n) -> new Item(entry.getKey(),r.getString("name"),r.getBigDecimal("price"),quantity),entry.getKey());
                if (product.isEmpty() || jdbc.update("UPDATE product_details SET stock_quantity=stock_quantity-? WHERE product_id=? AND stock_quantity>=?",quantity,entry.getKey(),quantity) != 1)
                    throw new IllegalArgumentException("An item has insufficient stock. Please review your bag. / 商品库存不足，请检查购物车。");
                lines.add(product.get(0)); total = total.add(product.get(0).subtotal());
            }
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO shop_orders(id,user_id,checkout_token,status,fulfillment,address,total) VALUES (?,?,?,'PENDING',?,?,?)",id,userId,token,fulfillment,destination,total);
            for (Item item : lines) jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,unit_price,quantity) VALUES (?,?,?,?,?)",id,item.productId(),item.productName(),item.unitPrice(),item.quantity());
            return id;
        });
    }
    public void edit(String id, String email, String fulfillment, String address) {
        ready(); String destination = address(fulfillment,address);
        tx.executeWithoutResult(s -> {
            get(id,email,false);
            if (jdbc.update("UPDATE shop_orders SET fulfillment=?,address=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",fulfillment,destination,id) != 1) locked();
        });
    }
    public void cancel(String id, String email) {
        ready();
        tx.executeWithoutResult(s -> {
            get(id,email,false);
            if (jdbc.update("UPDATE shop_orders SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",id) != 1) locked();
            for (Item item : items(id)) jdbc.update("UPDATE product_details SET stock_quantity=stock_quantity+? WHERE product_id=?",item.quantity(),item.productId());
        });
    }
    private void locked() { throw new IllegalArgumentException("The order has changed. Only pending orders can be edited or cancelled. / 订单状态已变化，仅待备货订单可修改或取消。"); }
    public void advance(String id, String next) {
        ready();
        String previous; String fulfillment = null;
        switch (next) {
            case "PREPARING" -> previous="PENDING";
            case "SHIPPED" -> { previous="PREPARING"; fulfillment="DELIVERY"; }
            case "READY" -> { previous="PREPARING"; fulfillment="COLLECTION"; }
            case "COMPLETED" -> previous=null;
            default -> throw new IllegalArgumentException("Invalid transition. / 无效操作。");
        }
        int changed;
        if (next.equals("COMPLETED")) changed=jdbc.update("UPDATE shop_orders SET status='COMPLETED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND ((status='SHIPPED' AND fulfillment='DELIVERY') OR (status='READY' AND fulfillment='COLLECTION'))",id);
        else if (fulfillment == null) changed=jdbc.update("UPDATE shop_orders SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status=?",next,id,previous);
        else changed=jdbc.update("UPDATE shop_orders SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status=? AND fulfillment=?",next,id,previous,fulfillment);
        if (changed != 1) throw new IllegalArgumentException("The order has changed; refresh and try again. / 订单状态已变化，请刷新后重试。");
    }
}
