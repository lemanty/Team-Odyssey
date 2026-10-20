package com.odyssey.retailshop.service;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VariantService {
 private final JdbcTemplate jdbc;
 public VariantService(ObjectProvider<JdbcTemplate> provider) {jdbc=provider.getIfAvailable();}
 public record Variant(long id,long productId,String size,String color,int stock) {}
 private Variant row(java.sql.ResultSet r,int n) throws java.sql.SQLException {
  return new Variant(r.getLong("id"),r.getLong("product_id"),r.getString("size_label"),r.getString("color_label"),r.getInt("stock_quantity"));
 }
 public List<Variant> list(long productId) {return jdbc==null?List.of():jdbc.query("SELECT * FROM product_variants WHERE product_id=? ORDER BY id",this::row,productId);}
 public Optional<Variant> find(long id) {return jdbc==null?Optional.empty():jdbc.query("SELECT * FROM product_variants WHERE id=?",this::row,id).stream().findFirst();}
 // Cart line IDs remain backwards compatible: positive product ID, negative variant ID.
 public long productId(long lineId) {return lineId>=0?lineId:find(-lineId).orElseThrow(()->new IllegalArgumentException("Product option unavailable. / 商品规格不可用。" )).productId();}
}
