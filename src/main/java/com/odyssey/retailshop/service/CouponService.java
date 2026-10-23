package com.odyssey.retailshop.service;

import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.access.AccessDeniedException;

@Service
public class CouponService {
    private final JdbcTemplate jdbc;
    public CouponService(ObjectProvider<JdbcTemplate> db){jdbc=db.getIfAvailable();}
    public record Coupon(long id,String code,String title,String kind,BigDecimal value,BigDecimal minimum,LocalDateTime starts,LocalDateTime ends,boolean stack,boolean enabled) {}
    public record Offer(Coupon coupon,String unavailable) {}
    public record Quote(MembershipService.Quote membership,Coupon coupon,BigDecimal discount) {}
    public record Snapshot(String code,String title,boolean stack,BigDecimal discount) {}
    private Coupon row(java.sql.ResultSet r,int n)throws java.sql.SQLException {
        return new Coupon(r.getLong("id"),r.getString("code"),r.getString("title"),r.getString("kind"),r.getBigDecimal("value_amount"),r.getBigDecimal("minimum_total"),r.getTimestamp("starts_at").toLocalDateTime(),r.getTimestamp("ends_at").toLocalDateTime(),r.getBoolean("stack_membership"),r.getBoolean("enabled"));
    }
    public List<Coupon> all(){return jdbc==null?List.of():jdbc.query("SELECT * FROM coupons ORDER BY created_at DESC,id DESC",this::row);}
    public List<Offer> offers(String email,BigDecimal original){
        if(jdbc==null)return List.of();
        Set<Long> used=new HashSet<>(jdbc.queryForList("SELECT cu.coupon_id FROM coupon_uses cu JOIN users u ON u.id=cu.user_id WHERE u.email=?",Long.class,email));
        LocalDateTime now=LocalDateTime.now();
        return all().stream().filter(c->c.enabled()&&c.ends().isAfter(now)).map(c->new Offer(c,used.contains(c.id())?"Already used / 已使用":now.isBefore(c.starts())?"Not started / 尚未开始":original.compareTo(c.minimum())<0?"Minimum not reached / 未达到使用门槛":"")).toList();
    }
    public Quote quote(String email,MembershipService.Quote member,String code,boolean lock){
        if(code==null||code.isBlank())return new Quote(member,null,BigDecimal.ZERO);
        if(jdbc==null)throw new IllegalArgumentException("Coupons require database mode. / 优惠券需要数据库模式。");
        if(lock&&!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Coupon requires checkout transaction");
        var c=jdbc.query("SELECT * FROM coupons WHERE code=?"+(lock?" FOR UPDATE":""),this::row,code.strip().toUpperCase(Locale.ROOT)).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Coupon not found. / 未找到该优惠券。"));
        LocalDateTime now=LocalDateTime.now();
        if(!c.enabled()||now.isBefore(c.starts())||!now.isBefore(c.ends()))throw new IllegalArgumentException("Coupon is inactive or outside its validity period. / 优惠券未生效、已过期或已停用。");
        if(member.original().compareTo(c.minimum())<0)throw new IllegalArgumentException("Coupon minimum is not reached. / 商品原价合计未达到优惠券使用门槛。");
        // Locking read sees the latest redemption even under MySQL REPEATABLE READ.
        var used=jdbc.queryForList("SELECT cu.order_id FROM coupon_uses cu JOIN users u ON u.id=cu.user_id WHERE cu.coupon_id=? AND u.email=?"+(lock?" FOR UPDATE":""),String.class,c.id(),email);
        if(!used.isEmpty())throw new IllegalArgumentException("This coupon has already been used. / 该优惠券已使用。");
        BigDecimal base=c.stack()?member.payable():member.original();
        BigDecimal discount=(c.kind().equals("FIXED")?c.value():base.multiply(c.value()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)).min(base);
        return new Quote(new MembershipService.Quote(member.tier(),c.stack()?member.percent():100,member.original(),c.stack()?member.discount():BigDecimal.ZERO,base.subtract(discount)),c,discount);
    }
    void save(String orderId,long userId,Quote quote){
        if(quote.coupon()==null)return;
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Coupon requires checkout transaction");
        var c=quote.coupon();
        jdbc.update("INSERT INTO order_coupons(order_id,coupon_id,code,title,kind,value_amount,minimum_total,stack_membership,discount_total) VALUES (?,?,?,?,?,?,?,?,?)",orderId,c.id(),c.code(),c.title(),c.kind(),c.value(),c.minimum(),c.stack(),quote.discount());
        jdbc.update("INSERT INTO coupon_uses(coupon_id,user_id,order_id) VALUES (?,?,?)",c.id(),userId,orderId);
    }
    void release(String orderId){jdbc.update("DELETE FROM coupon_uses WHERE order_id=?",orderId);}
    public Snapshot snapshot(String orderId){return jdbc==null?null:jdbc.query("SELECT code,title,stack_membership,discount_total FROM order_coupons WHERE order_id=?",(r,n)->new Snapshot(r.getString(1),r.getString(2),r.getBoolean(3),r.getBigDecimal(4)),orderId).stream().findFirst().orElse(null);}
    private long manager(String email){
        if(jdbc==null)throw new IllegalArgumentException("Database mode required. / 需要数据库模式。");
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.getName().equals(email)||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new AccessDeniedException("Administrator required");
        return jdbc.queryForList("SELECT id FROM users WHERE email=? AND role='ADMIN'",Long.class,email).stream().findFirst().orElseThrow(()->new AccessDeniedException("Administrator required"));
    }
    public void create(String email,String code,String title,String kind,BigDecimal value,BigDecimal minimum,LocalDateTime starts,LocalDateTime ends,boolean stack){
        long actor=manager(email);
        code=code==null?"":code.strip().toUpperCase(Locale.ROOT);title=title==null?"":title.strip();
        if(!code.matches("[A-Z0-9_-]{3,40}")||title.isBlank()||title.length()>100)throw new IllegalArgumentException("Use a 3–40 character code (letters, digits, - or _) and a title up to 100 characters. / 券码须为3–40位英文字母、数字、横线或下划线，名称为1–100字。");
        if(!Set.of("FIXED","PERCENT").contains(kind)||value==null||value.signum()<=0||value.stripTrailingZeros().scale()>2||value.compareTo(new BigDecimal(kind.equals("PERCENT")?"100":"999999.99"))>0||minimum==null||minimum.signum()<0||minimum.stripTrailingZeros().scale()>2||minimum.compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("Check discount and minimum amounts. Percent off must be above 0 and at most 100. / 请检查优惠金额及门槛，减免百分比须大于0且不超过100。");
        if(kind.equals("FIXED") && value.compareTo(minimum)>0)throw new IllegalArgumentException("Fixed discount cannot exceed the minimum spend. / 固定优惠金额不能大于使用门槛金额。");
        if(starts==null||ends==null||!ends.isAfter(starts)||!ends.isAfter(LocalDateTime.now()))throw new IllegalArgumentException("End time must be after start time and in the future. / 结束时间须晚于开始时间和当前时间。");
        try{jdbc.update("INSERT INTO coupons(code,title,kind,value_amount,minimum_total,starts_at,ends_at,stack_membership,created_by) VALUES (?,?,?,?,?,?,?,?,?)",code,title,kind,value,minimum,starts,ends,stack,actor);}
        catch(org.springframework.dao.DuplicateKeyException e){throw new IllegalArgumentException("Coupon code already exists. / 券码已存在，请更换。");}
    }
    public void toggle(String email,long id,boolean enabled){manager(email);if(jdbc.update("UPDATE coupons SET enabled=? WHERE id=?",enabled,id)!=1)throw new IllegalArgumentException("Coupon not found. / 未找到优惠券。");}
}
