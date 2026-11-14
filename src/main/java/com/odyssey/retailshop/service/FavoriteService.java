package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Product favourites and the stock alerts they generate when a sold-out option returns. */
@Service
public class FavoriteService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public FavoriteService(ObjectProvider<JdbcTemplate> database,ObjectProvider<PlatformTransactionManager> manager){
        jdbc=database.getIfAvailable();var managerBean=manager.getIfAvailable();tx=managerBean==null?null:new TransactionTemplate(managerBean);
    }
    public boolean available(){return jdbc!=null&&tx!=null;}
    public record Favorite(long productId,String name,String nameZh,BigDecimal price,String image,int stock){}
    public record Notification(long id,long productId,String name,String nameZh,String image,String size,String color,LocalDateTime createdAt,boolean read){}

    private long account(String email){
        if(email==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return jdbc.queryForList("SELECT id FROM users WHERE email=? AND role<>'DELETED'",Long.class,email).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
    public boolean favorited(long product,String email){
        if(!available()||email==null)return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM product_favorites f JOIN users u ON u.id=f.user_id WHERE f.product_id=? AND u.email=?",Integer.class,product,email)>0;
    }
    /** Returns true when the product is now saved, false when the previous favourite was removed. */
    public boolean toggle(long product,String email){
        if(!available())throw new IllegalArgumentException("Favorites require database mode. / 收藏需要数据库模式。");
        return Boolean.TRUE.equals(tx.execute(status->{
            long userId=account(email);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Integer.class,product)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if(jdbc.update("DELETE FROM product_favorites WHERE user_id=? AND product_id=?",userId,product)>0)return false;
            jdbc.update("INSERT INTO product_favorites(user_id,product_id) VALUES (?,?)",userId,product);
            return true;
        }));
    }
    public List<Favorite> favorites(String email){
        if(!available()||email==null)return List.of();
        return jdbc.query("""
            SELECT p.id,p.name,p.price,d.garment_image,d.stock_quantity,m.name_zh
            FROM product_favorites f JOIN products p ON p.id=f.product_id
            LEFT JOIN product_details d ON d.product_id=p.id
            LEFT JOIN product_management m ON m.product_id=p.id
            WHERE f.user_id=? ORDER BY f.created_at DESC,p.id DESC
            """,(r,n)->new Favorite(r.getLong(1),r.getString(2),r.getString(6),r.getBigDecimal(3),r.getString(4),r.getInt(5)),account(email));
    }
    public List<Notification> notifications(String email){
        if(!available()||email==null)return List.of();
        return jdbc.query("""
            SELECT n.id,n.product_id,p.name,d.garment_image,m.name_zh,n.size_label,n.color_label,n.created_at,n.read_at
            FROM stock_notifications n JOIN products p ON p.id=n.product_id
            LEFT JOIN product_details d ON d.product_id=p.id
            LEFT JOIN product_management m ON m.product_id=p.id
            WHERE n.user_id=? ORDER BY n.created_at DESC,n.id DESC
            """,(r,n)->new Notification(r.getLong(1),r.getLong(2),r.getString(3),r.getString(5),r.getString(4),
                r.getString(6),r.getString(7),r.getTimestamp(8).toLocalDateTime(),r.getTimestamp(9)!=null),account(email));
    }
    public int unread(String email){
        if(!available()||email==null)return 0;
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_notifications WHERE user_id=? AND read_at IS NULL",Integer.class,account(email));
    }
    public void markRead(long notification,String email){
        if(!available()||email==null)return;
        jdbc.update("UPDATE stock_notifications SET read_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND read_at IS NULL",notification,account(email));
    }
    public void markAllRead(String email){
        if(!available()||email==null)return;
        jdbc.update("UPDATE stock_notifications SET read_at=CURRENT_TIMESTAMP WHERE user_id=? AND read_at IS NULL",account(email));
    }
    /** Called inside the product save transaction when an option moves from zero to positive stock. */
    public void notifyRestock(long product,long variant,String size,String color){
        if(!available())return;
        // One unread alert per account and option; reading it re-arms the next restock.
        var accounts=jdbc.queryForList("""
            SELECT f.user_id FROM product_favorites f
            WHERE f.product_id=? AND NOT EXISTS (
                SELECT 1 FROM stock_notifications n
                WHERE n.user_id=f.user_id AND n.product_id=? AND n.variant_id=? AND n.read_at IS NULL)
            """,Long.class,product,product,variant);
        for(long userId:accounts)jdbc.update("INSERT INTO stock_notifications(user_id,product_id,variant_id,size_label,color_label) VALUES (?,?,?,?,?)",userId,product,variant,size,color);
    }
}
