package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Called inside the order transaction, after locking its owning user. */
@Service
public class OrderPaymentService {
    private final JdbcTemplate jdbc;
    private final WalletService wallets;
    public OrderPaymentService(ObjectProvider<JdbcTemplate> jdbc,WalletService wallets){this.jdbc=jdbc.getIfAvailable();this.wallets=wallets;}
    public record Payment(String method,String status,BigDecimal amount) {}
    // The caller must authorize access to this order before reading payment details.
    public Payment snapshot(String orderId) {
        if(jdbc==null)return new Payment("LEGACY","UNRECORDED",BigDecimal.ZERO);
        return jdbc.query("SELECT method,status,amount FROM order_payments WHERE order_id=?",(r,n)->new Payment(r.getString(1),r.getString(2),r.getBigDecimal(3)),orderId).stream().findFirst().orElse(new Payment("LEGACY","UNRECORDED",BigDecimal.ZERO));
    }
    private void transactionRequired(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Payment must run in the order transaction");}
    void pay(String orderId,long userId,String method,BigDecimal amount) {
        transactionRequired();
        if(!java.util.Set.of("WALLET","OFFLINE").contains(method))throw new IllegalArgumentException("Choose a payment method. / 请选择有效的支付方式。");
        Long entry=null;
        if(method.equals("WALLET")) {
            BigDecimal balance=wallets.balance(userId);
            if(balance.compareTo(amount)<0)throw new IllegalArgumentException("Insufficient wallet balance. No payment was taken; your bag is unchanged. / 储值余额不足，未扣款，购物车已保留。");
            if(amount.signum()>0)entry=entry(userId,amount.negate(),balance.subtract(amount),"Order payment / 订单支付");
        }
        jdbc.update("INSERT INTO order_payments(order_id,method,status,amount,payment_entry_id,paid_at) VALUES (?,?,?,?,?,?)",orderId,method,method.equals("WALLET")?"PAID":"UNPAID",amount,entry,method.equals("WALLET")?java.sql.Timestamp.from(java.time.Instant.now()):null);
    }
    void refund(String orderId,long userId) {
        transactionRequired();
        var payment=snapshot(orderId);
        if(payment.method().equals("LEGACY") || payment.status().equals("REFUNDED") || payment.status().equals("CANCELLED"))return;
        if(payment.method().equals("OFFLINE")) {jdbc.update("UPDATE order_payments SET status='CANCELLED' WHERE order_id=? AND status='UNPAID'",orderId);return;}
        Long entry=null;
        if(payment.amount().signum()>0) {
            BigDecimal after=wallets.balance(userId).add(payment.amount());
            if(after.compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("The refund would exceed the wallet limit. Contact an administrator to resolve the balance before cancelling. / 退款将超过储值余额上限，请联系管理员处理余额后再取消。");
            entry=entry(userId,payment.amount(),after,"Order cancellation refund / 取消订单退款");
        }
        jdbc.update("UPDATE order_payments SET status='REFUNDED',refund_entry_id=?,refunded_at=CURRENT_TIMESTAMP WHERE order_id=? AND status='PAID'",entry,orderId);
    }
    private long entry(long userId,BigDecimal amount,BigDecimal after,String note) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO wallet_entries(user_id,actor_id,request_token,amount,balance_after,note) VALUES (?,?,?,?,?,?)",new String[]{"id"});
            ps.setLong(1,userId);ps.setLong(2,userId);ps.setString(3,UUID.randomUUID().toString());ps.setBigDecimal(4,amount);ps.setBigDecimal(5,after);ps.setString(6,note);return ps;},key);
        return key.getKey().longValue();
    }
}
