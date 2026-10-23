package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public ReviewService(ObjectProvider<JdbcTemplate> database,ObjectProvider<PlatformTransactionManager> manager){
        jdbc=database.getIfAvailable();var tm=manager.getIfAvailable();tx=tm==null?null:new TransactionTemplate(tm);
    }
    public boolean available(){return jdbc!=null&&tx!=null;}
    public record Review(long id,String author,int rating,String body,boolean purchased,String size,String color,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record Purchase(String key,String size,String color,LocalDateTime date) {}
    public record Page(List<Review> reviews,long total,BigDecimal average,boolean more) {}
    private Review row(java.sql.ResultSet r,int n)throws java.sql.SQLException{
        return new Review(r.getLong("id"),r.getString("role").equals("DELETED")?"":r.getString("display_name"),r.getInt("rating"),r.getString("body"),r.getString("order_id")!=null,r.getString("size_label"),r.getString("color_label"),r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("updated_at").toLocalDateTime());
    }
    private String select(){return "SELECT r.*,u.display_name,u.role FROM product_reviews r JOIN users u ON u.id=r.user_id ";}
    public Page page(long product,int page){
        if(!available())return new Page(List.of(),0,BigDecimal.ZERO,false);
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE product_id=?",Long.class,product);
        BigDecimal avg=jdbc.queryForObject("SELECT COALESCE(AVG(CAST(rating AS DECIMAL(10,2))),0) FROM product_reviews WHERE product_id=?",BigDecimal.class,product);
        var rows=jdbc.query(select()+"WHERE r.product_id=? ORDER BY r.created_at DESC,r.id DESC LIMIT 21 OFFSET ?",this::row,product,Math.max(0,Math.min(page,100000))*20);
        return new Page(rows.subList(0,Math.min(rows.size(),20)),count,avg,rows.size()>20);
    }
    public Review own(long product,String email){if(!available()||email==null)return null;return jdbc.query(select()+"WHERE r.product_id=? AND u.email=?",this::row,product,email).stream().findFirst().orElse(null);}
    public String selected(long product,String email){if(!available()||email==null)return "";return jdbc.query("SELECT r.order_id,r.variant_id FROM product_reviews r JOIN users u ON u.id=r.user_id WHERE r.product_id=? AND u.email=? AND r.order_id IS NOT NULL",(r,n)->r.getString(1)+":"+r.getLong(2),product,email).stream().findFirst().orElse("");}
    public List<Purchase> purchases(long product,String email){
        if(!available()||email==null)return List.of();
        return jdbc.query("SELECT i.order_id,i.variant_id,i.size_label,i.color_label,o.created_at FROM order_all_items i JOIN shop_orders o ON o.id=i.order_id JOIN users u ON u.id=o.user_id WHERE i.product_id=? AND u.email=? AND o.status='COMPLETED' ORDER BY o.created_at DESC,o.id,i.variant_id",(r,n)->new Purchase(r.getString(1)+":"+r.getLong(2),r.getString(3),r.getString(4),r.getTimestamp(5).toLocalDateTime()),product,email);
    }
    public void save(long product,String email,int rating,String body,String purchaseKey){
        if(!available())throw new IllegalArgumentException("Reviews require database mode. / 评论需要数据库模式。");
        if(rating<1||rating>5||body==null||body.isBlank()||body.strip().length()>2000)throw new IllegalArgumentException("Choose 1–5 stars and enter a review within 2000 characters. / 请选择1–5星，填写1–2000字的评论。");
        tx.executeWithoutResult(s->{
            var ids=jdbc.queryForList("SELECT id FROM users WHERE email=? AND role<>'DELETED' FOR UPDATE",Long.class,email);
            if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            long uid=ids.get(0);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=? AND NOT EXISTS (SELECT 1 FROM product_management m WHERE m.product_id=products.id AND m.published=FALSE)",Integer.class,product)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            var choices=purchases(product,email);
            String key=purchaseKey==null?"":purchaseKey.strip();
            // Purchase labels are copied from this user's completed order, never from form fields.
            Purchase purchase=key.isEmpty()?(choices.isEmpty()?null:choices.get(0)):choices.stream().filter(p->p.key().equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose a completed purchase of this product. / 请选择本人已完成订单中的该商品规格。"));
            String order=null,size="",color="";long variant=0;
            if(purchase!=null){String[] parts=purchase.key().split(":");order=parts[0];variant=Long.parseLong(parts[1]);size=purchase.size();color=purchase.color();}
            int changed=jdbc.update("UPDATE product_reviews SET rating=?,body=?,order_id=?,variant_id=?,size_label=?,color_label=?,updated_at=CURRENT_TIMESTAMP WHERE product_id=? AND user_id=?",rating,body.strip(),order,variant,size,color,product,uid);
            if(changed==0&&jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE product_id=? AND user_id=?",Integer.class,product,uid)==0)
                jdbc.update("INSERT INTO product_reviews(product_id,user_id,rating,body,order_id,variant_id,size_label,color_label) VALUES (?,?,?,?,?,?,?,?)",product,uid,rating,body.strip(),order,variant,size,color);
        });
    }
    public void delete(long product,String email){
        if(!available())throw new IllegalArgumentException("Reviews require database mode. / 评论需要数据库模式。");
        tx.executeWithoutResult(s->{
            var ids=jdbc.queryForList("SELECT id FROM users WHERE email=? AND role<>'DELETED' FOR UPDATE",Long.class,email);
            if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            jdbc.update("DELETE FROM product_reviews WHERE product_id=? AND user_id=?",product,ids.get(0));
        });
    }
}
