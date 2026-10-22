package com.odyssey.retailshop.service;

import java.math.*;
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
public class ReturnService {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx;
    private final OrderService orders; private final OrderPaymentService payments;
    public ReturnService(ObjectProvider<JdbcTemplate> db,ObjectProvider<PlatformTransactionManager> manager,OrderService orders,OrderPaymentService payments) {
        jdbc=db.getIfAvailable(); var tm=manager.getIfAvailable(); tx=tm==null?null:new TransactionTemplate(tm); this.orders=orders;this.payments=payments;
    }
    public record Request(String id,String orderId,long userId,long productId,long variantId,int quantity,String reason,String status,BigDecimal refund,String method,boolean restocked,boolean image,String customer,String product,String size,String color,LocalDateTime createdAt) {}
    public record Event(String status,String note,String actor,LocalDateTime time) {}
    private String select(){return "SELECT r.id,r.order_id,r.user_id,r.product_id,r.variant_id,r.quantity,r.reason,r.status,r.refund_amount,r.refund_method,r.restocked,r.created_at,(r.image_data IS NOT NULL) AS has_image,u.display_name,i.product_name,i.size_label,i.color_label FROM return_requests r JOIN users u ON u.id=r.user_id JOIN order_all_items i ON i.order_id=r.order_id AND i.product_id=r.product_id AND i.variant_id=r.variant_id ";}
    private Request row(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Request(r.getString("id"),r.getString("order_id"),r.getLong("user_id"),r.getLong("product_id"),r.getLong("variant_id"),r.getInt("quantity"),r.getString("reason"),r.getString("status"),r.getBigDecimal("refund_amount"),r.getString("refund_method"),r.getBoolean("restocked"),r.getBoolean("has_image"),r.getString("display_name"),r.getString("product_name"),r.getString("size_label"),r.getString("color_label"),r.getTimestamp("created_at").toLocalDateTime());}
    public List<Request> list(String email,boolean staff){if(jdbc==null)return List.of();return jdbc.query(select()+(staff?"":" WHERE u.email=?")+" ORDER BY CASE r.status WHEN 'REQUESTED' THEN 0 WHEN 'RECEIVED' THEN 1 WHEN 'APPROVED' THEN 2 ELSE 3 END,r.created_at DESC",this::row,staff?new Object[]{}:new Object[]{email});}
    public Request get(String id,String email,boolean staff){if(jdbc==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);return jdbc.query(select()+" WHERE r.id=?"+(staff?"":" AND u.email=?"),this::row,staff?new Object[]{id}:new Object[]{id,email}).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    public BigDecimal refunded(String orderId){return jdbc==null?BigDecimal.ZERO:jdbc.queryForObject("SELECT COALESCE(SUM(refund_amount),0) FROM return_requests WHERE order_id=? AND status='COMPLETED'",BigDecimal.class,orderId);}
    public List<Event> events(String id){return jdbc.query("SELECT e.*,u.display_name FROM return_events e JOIN users u ON u.id=e.actor_id WHERE return_id=? ORDER BY e.id",(r,n)->new Event(r.getString("status"),r.getString("note"),r.getString("display_name"),r.getTimestamp("created_at").toLocalDateTime()),id);}
    public SupportImage image(String id,String email,boolean staff){get(id,email,staff);return jdbc.query("SELECT image_data,image_type FROM return_requests WHERE id=? AND image_data IS NOT NULL",(r,n)->new SupportImage(r.getBytes(1),r.getString(2)),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    public String create(String orderId,String email,long itemKey,int quantity,String reason,String token,SupportImage image){
        var order=orders.get(orderId,email,false);
        if(reason==null||reason.isBlank()||reason.strip().length()>1000||quantity<1)throw new IllegalArgumentException("Enter a reason and valid quantity. / 请填写原因及有效数量。");
        try{UUID.fromString(token);}catch(RuntimeException e){throw new IllegalArgumentException("Reload this page. / 请刷新页面。");}
        return tx.execute(s->{
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,order.userId());
            jdbc.queryForObject("SELECT id FROM shop_orders WHERE id=? FOR UPDATE",String.class,orderId);
            var retries=jdbc.queryForList("SELECT id FROM return_requests WHERE user_id=? AND request_token=?",String.class,order.userId(),token);if(!retries.isEmpty())return retries.get(0);
            if(!orders.get(orderId,email,false).status().equals("COMPLETED"))throw new IllegalArgumentException("Confirm receipt before requesting a return. / 请确认收货后再申请售后。");
            var item=orders.items(orderId).stream().filter(i->(i.variantId()>0?-i.variantId():i.productId())==itemKey).findFirst().orElseThrow(()->new IllegalArgumentException("Invalid item. / 无效商品。"));
            int reserved=jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM return_requests WHERE order_id=? AND product_id=? AND variant_id=? AND status<>'REJECTED'",Integer.class,orderId,item.productId(),item.variantId());
            if(quantity>item.quantity()-reserved)throw new IllegalArgumentException("Quantity exceeds items available to return. / 数量超过可退数量，请检查已有申请。");
            String id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO return_requests(id,order_id,user_id,product_id,variant_id,quantity,reason,request_token,image_data,image_type) VALUES (?,?,?,?,?,?,?,?,?,?)",id,orderId,order.userId(),item.productId(),item.variantId(),quantity,reason.strip(),token,image==null?null:image.bytes(),image==null?null:image.type());
            event(id,order.userId(),"REQUESTED",reason.strip());return id;
        });
    }
    private void event(String id,long actor,String status,String note){jdbc.update("INSERT INTO return_events(return_id,actor_id,status,note) VALUES (?,?,?,?)",id,actor,status,note);}
    public void process(String id,String email,String next,String note,boolean restock,boolean refund,boolean offlineConfirmed){
        if(note==null||note.isBlank()||note.strip().length()>1000)throw new IllegalArgumentException("Record a result within 1000 characters. / 请填写1000字以内的处理记录。");
        var initial=get(id,email,true);
        long actor=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        tx.executeWithoutResult(s->{
            // Same owner-first lock order as checkout/cancellation; serializes all refund attempts.
            for(long uid:new TreeSet<>(List.of(initial.userId(),actor)))jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",uid);
            String role=jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,actor);
            if(!Set.of("STAFF","ADMIN").contains(role))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            jdbc.queryForObject("SELECT id FROM shop_orders WHERE id=? FOR UPDATE",String.class,initial.orderId());
            jdbc.queryForObject("SELECT id FROM return_requests WHERE id=? FOR UPDATE",String.class,id);
            var r=get(id,email,true);if(r.status().equals(next))return;
            boolean valid=(r.status().equals("REQUESTED")&&Set.of("APPROVED","REJECTED").contains(next))||(r.status().equals("APPROVED")&&next.equals("RECEIVED"))||(r.status().equals("RECEIVED")&&next.equals("COMPLETED"));
            if(!valid)throw new IllegalArgumentException("This request has changed. Refresh and try again. / 申请状态已变更，请刷新后重试。");
            if(next.equals("COMPLETED")){
                BigDecimal amount=refund?refundAmount(r):BigDecimal.ZERO; Long entry=null;String method="NONE";
                if(refund){
                    var payment=payments.snapshot(r.orderId());
                    if(payment.method().equals("WALLET")&&!payment.status().equals("PAID"))throw new IllegalArgumentException("Payment is not refundable. / 当前支付状态不允许再次退款。");
                    if(payment.method().equals("WALLET")){method="WALLET";entry=payments.returnRefund(r.userId(),amount);}
                    else {if(!offlineConfirmed)throw new IllegalArgumentException("Confirm the offline refund has actually been completed. / 请确认线下退款已实际完成。");method="OFFLINE";}
                }
                if(restock){
                    jdbc.queryForList("SELECT id FROM products WHERE id=? FOR UPDATE",r.productId());
                    if(r.variantId()>0)jdbc.update("UPDATE product_variants SET stock_quantity=stock_quantity+? WHERE id=?",r.quantity(),r.variantId());
                    jdbc.update("UPDATE product_details SET stock_quantity=stock_quantity+? WHERE product_id=?",r.quantity(),r.productId());
                }
                jdbc.update("UPDATE return_requests SET refund_amount=?,refund_method=?,refund_entry_id=?,restocked=? WHERE id=?",amount,method,entry,restock,id);
            }
            jdbc.update("UPDATE return_requests SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",next,id);event(id,actor,next,note.strip());
        });
    }
    // Allocate the original discounted total across lines first, then quantities. Cumulative rounding preserves every cent.
    private BigDecimal refundAmount(Request r){
        var lines=new ArrayList<>(orders.items(r.orderId()));lines.sort(Comparator.comparingLong(OrderService.Item::productId).thenComparingLong(OrderService.Item::variantId));
        BigDecimal total=jdbc.queryForObject("SELECT total FROM shop_orders WHERE id=?",BigDecimal.class,r.orderId());
        BigDecimal subtotal=lines.stream().map(OrderService.Item::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add),cumulative=BigDecimal.ZERO,previous=BigDecimal.ZERO;
        for(var item:lines){cumulative=cumulative.add(item.subtotal());BigDecimal allocated=subtotal.signum()==0?BigDecimal.ZERO:total.multiply(cumulative).divide(subtotal,2,RoundingMode.HALF_UP);BigDecimal line=allocated.subtract(previous);previous=allocated;
            if(item.productId()==r.productId()&&item.variantId()==r.variantId()){
                int refunded=jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM return_requests WHERE order_id=? AND product_id=? AND variant_id=? AND status='COMPLETED' AND refund_method IN ('WALLET','OFFLINE')",Integer.class,r.orderId(),r.productId(),r.variantId());
                return line.multiply(BigDecimal.valueOf(refunded+r.quantity())).divide(BigDecimal.valueOf(item.quantity()),2,RoundingMode.HALF_UP).subtract(line.multiply(BigDecimal.valueOf(refunded)).divide(BigDecimal.valueOf(item.quantity()),2,RoundingMode.HALF_UP));
            }
        }throw new IllegalArgumentException("Order item missing");
    }
}
