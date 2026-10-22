package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;

@Service
public class WalletService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public WalletService(ObjectProvider<JdbcTemplate> provider, ObjectProvider<PlatformTransactionManager> manager) {
        jdbc=provider.getIfAvailable(); var tm=manager.getIfAvailable();
        tx=tm==null?null:new TransactionTemplate(tm);
    }
    public record Account(long id,String name,String email) {}
    public record Entry(BigDecimal amount,BigDecimal balance,String note,String actor,LocalDateTime time,String orderId,String kind) {}
    public boolean available(){return jdbc!=null && tx!=null;}
    public Account account(String email) {
        if(!available()) throw new IllegalArgumentException("Wallet requires database mode. / 储值需要数据库模式。");
        return jdbc.query("SELECT id,display_name,email FROM users WHERE email=? AND role<>'DELETED'",(r,n)->new Account(r.getLong(1),r.getString(2),r.getString(3)),email.strip())
            .stream().findFirst().orElseThrow(()->new IllegalArgumentException("Account not found. / 未找到该账号。"));
    }
    public BigDecimal balance(long id){return jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entries WHERE user_id=?",BigDecimal.class,id);}
    public List<Entry> history(long id) {
        return jdbc.query("SELECT w.*,u.display_name AS actor,p.order_id,CASE WHEN p.payment_entry_id=w.id THEN 'PAYMENT' WHEN p.refund_entry_id=w.id THEN 'REFUND' ELSE 'ADJUSTMENT' END AS kind FROM wallet_entries w JOIN users u ON u.id=w.actor_id LEFT JOIN order_payments p ON p.payment_entry_id=w.id OR p.refund_entry_id=w.id WHERE w.user_id=? ORDER BY w.id DESC LIMIT 100",(r,n)->new Entry(r.getBigDecimal("amount"),r.getBigDecimal("balance_after"),r.getString("note"),r.getString("actor"),r.getTimestamp("created_at").toLocalDateTime(),r.getString("order_id"),r.getString("kind")),id);
    }
    public void adjust(String actorEmail,String targetEmail,String direction,BigDecimal amount,String note,String token) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.getName().equals(actorEmail) || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN"))) throw new AccessDeniedException("Administrator required");
        if(amount==null || amount.signum()<=0 || amount.compareTo(new BigDecimal("999999.99"))>0 || amount.stripTrailingZeros().scale()>2) throw new IllegalArgumentException("Enter a positive amount, up to 999999.99, with at most two decimals. / 金额须大于0、不超过999999.99且最多两位小数。");
        if(!Set.of("CREDIT","DEBIT").contains(direction))throw new IllegalArgumentException("Invalid adjustment. / 无效操作。");
        if(note==null || note.isBlank() || note.strip().length()>300)throw new IllegalArgumentException("Enter a reason within 300 characters. / 请填写300字以内的调整原因。");
        try{UUID.fromString(token);}catch(RuntimeException e){throw new IllegalArgumentException("Reopen this page. / 请重新打开页面。");}
        var actor=account(actorEmail);var target=account(targetEmail);
        BigDecimal delta=direction.equals("CREDIT")?amount:amount.negate();
        tx.executeWithoutResult(status->{
            // Deterministic locks serialize balance edits and duplicate requests.
            for(long id:new TreeSet<>(List.of(actor.id(),target.id())))jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",id);
            if(!"ADMIN".equals(jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,actor.id())))throw new AccessDeniedException("Administrator required");
            if("DELETED".equals(jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,target.id())))throw new IllegalArgumentException("Account deleted. / 账号已删除。");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE actor_id=? AND request_token=?",Integer.class,actor.id(),token)>0)return;
            BigDecimal after=balance(target.id()).add(delta);
            if(after.signum()<0 || after.compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("Insufficient balance or balance limit exceeded. / 余额不足或超过余额上限。");
            jdbc.update("INSERT INTO wallet_entries(user_id,actor_id,request_token,amount,balance_after,note) VALUES (?,?,?,?,?,?)",target.id(),actor.id(),token,delta,after,note.strip());
        });
    }
}
