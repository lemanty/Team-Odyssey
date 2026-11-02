package com.odyssey.retailshop.service;

import java.math.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {
    private final JdbcTemplate jdbc;
    public MembershipService(ObjectProvider<JdbcTemplate> provider) { jdbc=provider.getIfAvailable(); }
    public record Level(String tier, int percent, BigDecimal spent, BigDecimal remaining, String nextTier) {}
    public record Quote(String tier, int percent, BigDecimal original, BigDecimal discount, BigDecimal payable) {}
    public static Level level(BigDecimal spent) {
        if (spent.compareTo(new BigDecimal("1000"))>=0) return new Level("Signature",85,spent,BigDecimal.ZERO,"");
        if (spent.compareTo(new BigDecimal("500"))>=0) return new Level("Gold",90,spent,new BigDecimal("1000").subtract(spent),"Signature");
        if (spent.compareTo(new BigDecimal("200"))>=0) return new Level("Silver",95,spent,new BigDecimal("500").subtract(spent),"Gold");
        return new Level("Essential",100,spent,new BigDecimal("200").subtract(spent),"Silver");
    }
    public Level forAccount(String email) {
        if (jdbc==null) return level(BigDecimal.ZERO);
        BigDecimal spent=jdbc.queryForObject("SELECT COALESCE(SUM(o.total-COALESCE((SELECT SUM(r.refund_amount) FROM return_requests r WHERE r.order_id=o.id AND r.status='COMPLETED'),0)),0) FROM shop_orders o JOIN users u ON u.id=o.user_id WHERE u.email=? AND o.status='COMPLETED'",BigDecimal.class,email);
        return level(spent);
    }
    public Quote quote(String email, BigDecimal original) {
        Level level=forAccount(email);
        BigDecimal total=original.multiply(BigDecimal.valueOf(level.percent())).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
        return new Quote(level.tier(),level.percent(),original,original.subtract(total),total);
    }
    public void save(String id, Quote quote) {
        jdbc.update("INSERT INTO order_membership(order_id,tier,percent_payable,original_total,discount_total) VALUES (?,?,?,?,?)",id,quote.tier(),quote.percent(),quote.original(),quote.discount());
    }
    // Caller must authorize access to the order before reading its pricing snapshot.
    public Quote snapshot(String id, BigDecimal total) {
        return jdbc.query("SELECT * FROM order_membership WHERE order_id=?",(r,n)->new Quote(r.getString("tier"),r.getInt("percent_payable"),r.getBigDecimal("original_total"),r.getBigDecimal("discount_total"),total),id)
            .stream().findFirst().orElse(new Quote("Essential",100,total,BigDecimal.ZERO,total));
    }
}
