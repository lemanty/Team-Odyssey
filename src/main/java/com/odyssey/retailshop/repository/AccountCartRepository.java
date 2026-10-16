package com.odyssey.retailshop.repository;

import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class AccountCartRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public AccountCartRepository(ObjectProvider<JdbcTemplate> jdbc, ObjectProvider<PlatformTransactionManager> manager) {
        this.jdbc=jdbc.getIfAvailable();
        var tm=manager.getIfAvailable(); this.tx=tm==null ? null : new TransactionTemplate(tm);
    }
    public boolean available() { return jdbc!=null; }
    public Map<Long,Integer> read(String email) {
        var values=new LinkedHashMap<Long,Integer>();
        jdbc.query("SELECT c.product_id,c.quantity FROM cart_items c JOIN users u ON u.id=c.user_id WHERE u.email=? ORDER BY c.product_id",r -> { values.put(r.getLong(1),r.getInt(2)); },email);
        return values;
    }
    public void modify(String email, Consumer<Map<Long,Integer>> change) {
        tx.executeWithoutResult(status -> {
            Long id=jdbc.queryForObject("SELECT id FROM users WHERE email=? FOR UPDATE",Long.class,email);
            Map<Long,Integer> items=read(email); change.accept(items);
            jdbc.update("DELETE FROM cart_items WHERE user_id=?",id);
            items.forEach((product,quantity)->jdbc.update("INSERT INTO cart_items(user_id,product_id,quantity) VALUES (?,?,?)",id,product,quantity));
        });
    }
}
