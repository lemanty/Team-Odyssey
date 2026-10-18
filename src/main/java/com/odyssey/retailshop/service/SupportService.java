package com.odyssey.retailshop.service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Each customer has a private thread; staff share the inbox but have individual read cursors. */
@Service
public class SupportService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public SupportService(ObjectProvider<JdbcTemplate> jdbc, ObjectProvider<PlatformTransactionManager> manager) {
        this.jdbc=jdbc.getIfAvailable(); var tm=manager.getIfAvailable();
        this.tx=tm==null ? null : new TransactionTemplate(tm);
    }
    public record Viewer(long id, String name, String role) {
        public boolean staff() { return role.equals("STAFF") || role.equals("ADMIN"); }
    }
    public record Conversation(String id, long customerId, String customerName, boolean waiting, boolean resolved,
                               boolean unread, long latestId, LocalDateTime updatedAt, String preview) {}
    public record Message(long id, long senderId, String senderName, boolean staff, String body, LocalDateTime createdAt, String orderId, boolean image) {}
    public record History(List<Message> messages, long before, long latestVisible) {}
    public record Counts(long waiting, long unread, long resolved) {}
    public Viewer viewer(String email) {
        if (jdbc==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Support requires MySQL mode.");
        return jdbc.query("SELECT id,display_name,role FROM users WHERE email=?",(r,n)->new Viewer(r.getLong(1),r.getString(2),r.getString(3)),email)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
    private String select() {
        return "SELECT c.*,u.display_name,COALESCE(rd.last_message_id,0) AS seen,"
            + "(SELECT m.body FROM support_messages m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1) AS preview "
            + "FROM support_conversations c JOIN users u ON u.id=c.customer_id LEFT JOIN support_reads rd ON rd.conversation_id=c.id AND rd.user_id=? ";
    }
    private Conversation row(ResultSet r, Viewer viewer) throws SQLException {
        long customer=r.getLong("last_customer_message"), staff=r.getLong("last_staff_message");
        return new Conversation(r.getString("id"),r.getLong("customer_id"),r.getString("display_name"),customer>staff,
            r.getBoolean("resolved"),(viewer.staff()?customer:staff)>r.getLong("seen"),Math.max(customer,staff),
            r.getTimestamp("updated_at").toLocalDateTime(),r.getString("preview"));
    }
    public Conversation own(Viewer viewer) {
        return jdbc.query(select()+"WHERE c.customer_id=?",(r,n)->row(r,viewer),viewer.id(),viewer.id()).stream().findFirst().orElse(null);
    }
    public Conversation get(Viewer viewer,String id) {
        return jdbc.query(select()+"WHERE c.id=?"+(viewer.staff()?"":" AND c.customer_id=?"),(r,n)->row(r,viewer),
            viewer.staff()?new Object[]{viewer.id(),id}:new Object[]{viewer.id(),id,viewer.id()})
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public List<Conversation> inbox(Viewer viewer,String filter,int page) {
        requireStaff(viewer);
        String condition=switch(filter) {
            case "waiting" -> "c.last_customer_message>c.last_staff_message";
            case "unread" -> "c.last_customer_message>COALESCE(rd.last_message_id,0)";
            case "resolved" -> "c.resolved=TRUE";
            default -> "1=1";
        };
        return jdbc.query(select()+"WHERE "+condition+" ORDER BY (c.last_customer_message>c.last_staff_message) DESC,c.updated_at DESC,c.id LIMIT 31 OFFSET ?",
            (r,n)->row(r,viewer),viewer.id(),page*30);
    }
    public Counts counts(Viewer viewer) {
        requireStaff(viewer);
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN c.last_customer_message>c.last_staff_message THEN 1 ELSE 0 END),0),"
            +"COALESCE(SUM(CASE WHEN c.last_customer_message>COALESCE(r.last_message_id,0) THEN 1 ELSE 0 END),0),"
            +"COALESCE(SUM(CASE WHEN c.resolved=TRUE THEN 1 ELSE 0 END),0) FROM support_conversations c LEFT JOIN support_reads r ON r.conversation_id=c.id AND r.user_id=?",
            (r,n)->new Counts(r.getLong(1),r.getLong(2),r.getLong(3)),viewer.id());
    }
    public History history(Viewer viewer,String id,long before) {
        get(viewer,id);
        List<Message> rows=jdbc.query("SELECT m.*,u.display_name,e.order_id,(e.image_data IS NOT NULL) AS has_image FROM support_messages m JOIN users u ON u.id=m.sender_id LEFT JOIN support_message_extras e ON e.message_id=m.id WHERE m.conversation_id=? AND m.id<? ORDER BY m.id DESC LIMIT 51",
            (r,n)->new Message(r.getLong("id"),r.getLong("sender_id"),r.getString("display_name"),!r.getString("sender_role").equals("CUSTOMER"),r.getString("body"),r.getTimestamp("created_at").toLocalDateTime(),r.getString("order_id"),r.getBoolean("has_image")),
            id,before>0?before:Long.MAX_VALUE);
        boolean more=rows.size()>50; if (more) rows=new ArrayList<>(rows.subList(0,50));
        Collections.reverse(rows);
        return new History(rows,more?rows.get(0).id():0,rows.isEmpty()?0:rows.get(rows.size()-1).id());
    }
    public record OrderPreview(String name,int quantity,String image) {}
    public Map<String,List<OrderPreview>> orderPreviews(Viewer viewer,String conversationId,History history) {
        var conversation=get(viewer,conversationId);
        var ids=history.messages().stream().map(Message::orderId).filter(Objects::nonNull).distinct().toList();
        Map<String,List<OrderPreview>> result=new LinkedHashMap<>();
        if(ids.isEmpty()) return result;
        List<Object> args=new ArrayList<>(ids); args.add(conversation.customerId());
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        jdbc.query("SELECT i.order_id,i.product_name,i.quantity,d.garment_image FROM order_items i JOIN shop_orders o ON o.id=i.order_id LEFT JOIN product_details d ON d.product_id=i.product_id WHERE i.order_id IN ("+placeholders+") AND o.user_id=? ORDER BY i.product_id",
            (org.springframework.jdbc.core.RowCallbackHandler) r -> result.computeIfAbsent(r.getString("order_id"),key->new ArrayList<>()).add(new OrderPreview(r.getString("product_name"),r.getInt("quantity"),r.getString("garment_image"))),args.toArray());
        return result;
    }
    public String send(Viewer viewer,String id,String body,String token) {
        return send(viewer,id,body,token,null,null);
    }
    public String send(Viewer viewer,String id,String body,String token,String orderId,SupportImage image) {
        String clean=body==null?"":body.strip();
        String order=orderId==null || orderId.isBlank()?null:orderId;
        if ((clean.isEmpty() && order==null && image==null) || clean.length()>2000) throw new IllegalArgumentException("Add text, an image or an order; text must be at most 2000 characters. / 请填写文字、添加图片或选择订单，文字最多2000字。");
        try { UUID.fromString(token); } catch(RuntimeException e) { throw new IllegalArgumentException("Reload the conversation and try again. / 请刷新对话后重试。"); }
        return tx.execute(status -> {
            // Serialize retries from one sender; lock the conversation before allocating message IDs.
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,viewer.id());
            if (id!=null) get(viewer,id);
            var retry=jdbc.queryForList("SELECT conversation_id FROM support_messages WHERE sender_id=? AND request_token=?",String.class,viewer.id(),token);
            if (!retry.isEmpty()) {
                String existing=retry.get(0); get(viewer,existing);
                if (id!=null && !id.equals(existing)) throw new ResponseStatusException(HttpStatus.CONFLICT);
                return existing;
            }
            String thread=id;
            if (thread==null) {
                if (viewer.staff()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                var existing=own(viewer);
                if (existing==null) {
                    thread=UUID.randomUUID().toString();
                    jdbc.update("INSERT INTO support_conversations(id,customer_id) VALUES (?,?)",thread,viewer.id());
                } else thread=existing.id();
            }
            jdbc.queryForObject("SELECT id FROM support_conversations WHERE id=? FOR UPDATE",String.class,thread);
            long customerId=get(viewer,thread).customerId();
            if(order!=null && jdbc.queryForObject("SELECT COUNT(*) FROM shop_orders WHERE id=? AND user_id=?",Integer.class,order,customerId)!=1)
                throw new IllegalArgumentException("Choose an order belonging to this customer. / 只能选择当前顾客自己的订单。");
            jdbc.update("INSERT INTO support_messages(conversation_id,sender_id,sender_role,body,request_token) VALUES (?,?,?,?,?)",thread,viewer.id(),viewer.role(),clean,token);
            Long message=jdbc.queryForObject("SELECT id FROM support_messages WHERE sender_id=? AND request_token=?",Long.class,viewer.id(),token);
            if(order!=null || image!=null) jdbc.update("INSERT INTO support_message_extras(message_id,order_id,image_data,image_type) VALUES (?,?,?,?)",
                message,order,image==null?null:image.bytes(),image==null?null:image.type());
            jdbc.update("UPDATE support_conversations SET "+(viewer.staff()?"last_staff_message":"last_customer_message")+"=?,resolved=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?",message,thread);
            return thread;
        });
    }
    public record OrderChoice(String id, java.math.BigDecimal total, String status, String products) {}
    public List<OrderChoice> orderChoices(Viewer viewer,String thread) {
        if(viewer.staff() && thread==null) return List.of();
        long customerId=thread==null?viewer.id():get(viewer,thread).customerId();
        var rows=jdbc.query("SELECT id,total,status FROM shop_orders WHERE user_id=? ORDER BY created_at DESC,id",(r,n)->new OrderChoice(r.getString(1),r.getBigDecimal(2),r.getString(3),""),customerId);
        return rows.stream().map(o -> new OrderChoice(o.id(),o.total(),o.status(),String.join(", ",jdbc.queryForList("SELECT product_name FROM order_items WHERE order_id=? ORDER BY product_id",String.class,o.id())))).toList();
    }
    public SupportImage image(Viewer viewer,long messageId) {
        String thread=jdbc.queryForList("SELECT conversation_id FROM support_messages WHERE id=?",String.class,messageId).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        get(viewer,thread);
        return jdbc.query("SELECT image_data,image_type FROM support_message_extras WHERE message_id=? AND image_data IS NOT NULL",
            (r,n)->new SupportImage(r.getBytes(1),r.getString(2)),messageId).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public void read(Viewer viewer,String id,long through) {
        tx.executeWithoutResult(status -> {
            get(viewer,id);
            jdbc.queryForObject("SELECT id FROM support_conversations WHERE id=? FOR UPDATE",String.class,id);
            Long visible=jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM support_messages WHERE conversation_id=? AND id<=?",Long.class,id,Math.max(0,through));
            int changed=jdbc.update("UPDATE support_reads SET last_message_id=GREATEST(last_message_id,?) WHERE conversation_id=? AND user_id=?",visible,id,viewer.id());
            if (changed==0 && jdbc.queryForObject("SELECT COUNT(*) FROM support_reads WHERE conversation_id=? AND user_id=?",Integer.class,id,viewer.id())==0)
                jdbc.update("INSERT INTO support_reads VALUES (?,?,?)",id,viewer.id(),visible);
        });
    }
    public void resolve(Viewer viewer,String id,long version) {
        requireStaff(viewer); get(viewer,id);
        if(jdbc.update("UPDATE support_conversations SET resolved=TRUE WHERE id=? AND last_customer_message<=last_staff_message AND GREATEST(last_customer_message,last_staff_message)=?",id,version)!=1)
            throw new IllegalArgumentException("Reply first, or refresh to see new messages. / 请先回复顾客，或刷新查看新消息。");
    }
    private void requireStaff(Viewer viewer) {
        if (!viewer.staff()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
