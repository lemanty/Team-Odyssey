package com.odyssey.retailshop.service;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AddressService {
 private final JdbcTemplate jdbc; private final TransactionTemplate tx;
 public AddressService(ObjectProvider<JdbcTemplate> j,ObjectProvider<PlatformTransactionManager> tm) {
  jdbc=j.getIfAvailable();var manager=tm.getIfAvailable();tx=manager==null?null:new TransactionTemplate(manager);
 }
 public boolean available(){return jdbc!=null;}
 public record Address(long id,String recipient,String phone,String addressLine,boolean isDefault){
  public String formatted(){return recipient+" · "+phone+"\n"+addressLine;}
 }
 private Address row(java.sql.ResultSet r,int n) throws java.sql.SQLException{return new Address(r.getLong("id"),r.getString("recipient"),r.getString("phone"),r.getString("address_line"),r.getBoolean("is_default"));}
 public List<Address> list(String email){return jdbc==null?List.of():jdbc.query("SELECT a.* FROM user_addresses a JOIN users u ON u.id=a.user_id WHERE u.email=? ORDER BY a.is_default DESC,a.id",this::row,email);}
 public Address get(String email,long id){return list(email).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 private long lock(String email){if(tx==null)throw new IllegalArgumentException("Addresses require database mode. / 地址功能需要数据库模式。");return jdbc.queryForObject("SELECT id FROM users WHERE email=? FOR UPDATE",Long.class,email);}
 private String clean(String text,int max){String s=text==null?"":text.strip();if(s.isBlank()||s.length()>max)throw new IllegalArgumentException("Complete all address fields within the length limits. / 请填写完整的收件人、电话和地址，且不要超过长度限制。");return s;}
 public void save(String email,Long id,String recipient,String phone,String line,boolean makeDefault){
  String r=clean(recipient,80),p=clean(phone,40),a=clean(line,350);
  if(tx==null)throw new IllegalArgumentException("Addresses require database mode. / 地址功能需要数据库模式。");
  tx.executeWithoutResult(status->{
   long user=lock(email);var existing=list(email);
   if(id!=null)get(email,id);
   if(id==null && existing.size()>=20)throw new IllegalArgumentException("You can save up to 20 addresses. / 最多保存20个地址。");
   boolean selected=makeDefault||existing.isEmpty()||(id!=null&&get(email,id).isDefault());
   if(selected)jdbc.update("UPDATE user_addresses SET is_default=FALSE WHERE user_id=?",user);
   if(id==null)jdbc.update("INSERT INTO user_addresses(user_id,recipient,phone,address_line,is_default) VALUES (?,?,?,?,?)",user,r,p,a,selected);
   else jdbc.update("UPDATE user_addresses SET recipient=?,phone=?,address_line=?,is_default=? WHERE id=? AND user_id=?",r,p,a,selected,id,user);
  });
 }
 public void setDefault(String email,long id){if(tx==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);tx.executeWithoutResult(s->{long user=lock(email);get(email,id);jdbc.update("UPDATE user_addresses SET is_default=FALSE WHERE user_id=?",user);jdbc.update("UPDATE user_addresses SET is_default=TRUE WHERE id=? AND user_id=?",id,user);});}
 public void delete(String email,long id){if(tx==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);tx.executeWithoutResult(s->{long user=lock(email);get(email,id);jdbc.update("DELETE FROM user_addresses WHERE id=? AND user_id=?",id,user);var remaining=list(email);if(!remaining.isEmpty()&&remaining.stream().noneMatch(Address::isDefault))jdbc.update("UPDATE user_addresses SET is_default=TRUE WHERE id=? AND user_id=?",remaining.get(0).id(),user);});}
}
