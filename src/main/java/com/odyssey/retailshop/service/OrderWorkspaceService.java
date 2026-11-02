package com.odyssey.retailshop.service;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class OrderWorkspaceService {
    private final JdbcTemplate jdbc;
    public OrderWorkspaceService(ObjectProvider<JdbcTemplate> db){jdbc=db.getIfAvailable();}
    public record Row(String id,String customer,String email,String status,String fulfillment,BigDecimal total,LocalDateTime date){}
    public record Results(List<Row> rows,Map<String,Long> counts,boolean more){}
    public Results search(String q,String status,String from,String to,int page){
        var counts=new LinkedHashMap<String,Long>();for(String key:List.of("ALL","PENDING","PREPARING","SHIPPED","READY","COMPLETED","CANCELLED"))counts.put(key,0L);
        if(!counts.containsKey(status))throw new IllegalArgumentException("Invalid status / 无效状态");
        String where=" WHERE 1=1";List<Object> args=new ArrayList<>();
        if(!q.isBlank()){where+=" AND (LOWER(o.id) LIKE ? ESCAPE '!' OR LOWER(u.display_name) LIKE ? ESCAPE '!' OR LOWER(u.email) LIKE ? ESCAPE '!')";String term="%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.addAll(List.of(term,term,term));}
        try{LocalDate start=from.isBlank()?null:LocalDate.parse(from),end=to.isBlank()?null:LocalDate.parse(to);if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("Invalid date range / 开始日期不能晚于结束日期");
            if(start!=null){where+=" AND o.created_at>=?";args.add(java.sql.Timestamp.valueOf(start.atStartOfDay()));}if(end!=null){where+=" AND o.created_at<?";args.add(java.sql.Timestamp.valueOf(end.plusDays(1).atStartOfDay()));}
        }catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Invalid date / 请输入有效日期");}
        if(jdbc==null)return new Results(List.of(),counts,false);
        String source=" FROM shop_orders o JOIN users u ON u.id=o.user_id";
        jdbc.query("SELECT o.status,COUNT(*)"+source+where+" GROUP BY o.status",(org.springframework.jdbc.core.RowCallbackHandler)r->{counts.put(r.getString(1),r.getLong(2));counts.put("ALL",counts.get("ALL")+r.getLong(2));},args.toArray());
        if(!status.equals("ALL")){where+=" AND o.status=?";args.add(status);}args.add(Math.max(0,Math.min(page,100000))*50);
        var rows=jdbc.query("SELECT o.*,u.display_name,u.email"+source+where+" ORDER BY CASE o.status WHEN 'PENDING' THEN 0 WHEN 'PREPARING' THEN 1 WHEN 'READY' THEN 2 WHEN 'SHIPPED' THEN 3 ELSE 4 END,CASE WHEN o.status IN ('PENDING','PREPARING','READY','SHIPPED') THEN o.created_at END ASC,o.created_at DESC,o.id LIMIT 51 OFFSET ?",(r,n)->new Row(r.getString("id"),r.getString("display_name"),r.getString("email"),r.getString("status"),r.getString("fulfillment"),r.getBigDecimal("total"),r.getTimestamp("created_at").toLocalDateTime()),args.toArray());
        return new Results(rows.subList(0,Math.min(50,rows.size())),counts,rows.size()>50);
    }
}
