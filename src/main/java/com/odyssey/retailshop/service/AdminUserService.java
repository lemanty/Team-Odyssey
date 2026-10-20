package com.odyssey.retailshop.service;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;

@Service
public class AdminUserService {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public AdminUserService(ObjectProvider<JdbcTemplate> j,ObjectProvider<PlatformTransactionManager> m){jdbc=j.getIfAvailable();var manager=m.getIfAvailable();tx=manager==null?null:new TransactionTemplate(manager);}
 public record Account(long id,String name,String email,String role,LocalDateTime created){}
 public boolean available(){return jdbc!=null;}
 public List<Account> list(String query,int page){
  return jdbc.query("SELECT id,display_name,email,role,created_at FROM users WHERE LOWER(display_name) LIKE ? OR LOWER(email) LIKE ? ORDER BY id LIMIT 51 OFFSET ?",(r,n)->new Account(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5).toLocalDateTime()),"%"+query.toLowerCase(Locale.ROOT)+"%","%"+query.toLowerCase(Locale.ROOT)+"%",page*50);
 }
 public void change(String actorEmail,long id,String expected,String role){
  var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  if(auth==null || !auth.getName().equals(actorEmail) || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new AccessDeniedException("Administrator required");
  if(tx==null)throw new IllegalArgumentException("Database mode required. / 需要数据库模式。");
  if(!Set.of("CUSTOMER","STAFF","ADMIN").contains(role))throw new IllegalArgumentException("Invalid role. / 无效角色。");
  tx.executeWithoutResult(s->{
   // Serialize role changes, including simultaneous administrator demotions.
   var rows=jdbc.queryForList("SELECT id,email,role FROM users ORDER BY id FOR UPDATE");
   var actor=rows.stream().filter(r->r.get("email").equals(actorEmail)).findFirst().orElseThrow(()->new AccessDeniedException("Administrator required"));
   if(!actor.get("role").equals("ADMIN"))throw new AccessDeniedException("Administrator required");
   var target=rows.stream().filter(r->((Number)r.get("id")).longValue()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Account not found. / 账号不存在。"));
   if(((Number)actor.get("id")).longValue()==id)throw new IllegalArgumentException("You cannot change your own role. / 不能修改自己的角色。");
   String old=(String)target.get("role");
   if(!old.equals(expected))throw new IllegalArgumentException("Role changed. Refresh before editing. / 角色已变化，请刷新后重试。");
   if(old.equals(role))return;
   jdbc.update("UPDATE users SET role=? WHERE id=?",role,id);
   jdbc.update("INSERT INTO account_role_audit(actor_id,user_id,old_role,new_role) VALUES (?,?,?,?)",actor.get("id"),id,old,role);
  });
 }
}
