package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ProductManagementService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final StoreText text;
    public ProductManagementService(ObjectProvider<JdbcTemplate> db, ObjectProvider<PlatformTransactionManager> tm, StoreText text) {
        jdbc=db.getIfAvailable(); var manager=tm.getIfAvailable(); tx=manager==null?null:new TransactionTemplate(manager); this.text=text;
    }
    public boolean available() { return jdbc!=null; }
    private void ready() { if(!available()) throw new IllegalArgumentException("Product management requires database mode. / 商品管理需要数据库模式。"); }
    public record Summary(long id,String name,String nameZh,String category,BigDecimal price,int stock,boolean published,int lowOptions,String image) {}
    public List<Summary> list(String query,String status) {
        ready(); String q=query.strip().toLowerCase(Locale.ROOT);
        return jdbc.query("""
            SELECT p.*, d.stock_quantity,d.garment_image,m.name_zh,COALESCE(m.published,TRUE) AS published,
            COALESCE(m.low_stock_threshold,5) AS threshold,
            (SELECT COUNT(*) FROM product_variants v WHERE v.product_id=p.id AND v.stock_quantity<=COALESCE(m.low_stock_threshold,5)) AS low_options
            FROM products p LEFT JOIN product_details d ON d.product_id=p.id
            LEFT JOIN product_management m ON m.product_id=p.id ORDER BY p.id DESC
            """,(r,n)->new Summary(r.getLong("id"),r.getString("name"),r.getString("name_zh")==null?text.text(r.getString("name"),true):r.getString("name_zh"),r.getString("category"),r.getBigDecimal("price"),r.getInt("stock_quantity"),r.getBoolean("published"),Math.max(r.getInt("low_options"),r.getInt("stock_quantity")<=r.getInt("threshold")?1:0),r.getString("garment_image")))
            .stream().filter(p->q.isBlank() || (p.name()+" "+p.nameZh()+" "+p.id()).toLowerCase(Locale.ROOT).contains(q))
            .filter(p->switch(status){case "published"->p.published();case "draft"->!p.published();case "low"->p.lowOptions()>0;default->true;}).toList();
    }
    public ProductEditForm edit(long id) {
        ready();
        var forms=jdbc.query("""
            SELECT p.*,d.audience,d.stock_quantity,d.garment_image,d.model_image,m.name_zh,m.description_zh,
            COALESCE(m.published,TRUE) AS published,COALESCE(m.low_stock_threshold,5) AS threshold,COALESCE(m.revision,0) AS revision
            FROM products p LEFT JOIN product_details d ON d.product_id=p.id LEFT JOIN product_management m ON m.product_id=p.id WHERE p.id=?
            """,(r,n)->{
            var f=new ProductEditForm(); f.setId(id);f.setName(r.getString("name"));f.setDescription(r.getString("description"));
            f.setNameZh(r.getString("name_zh")==null?text.text(f.getName(),true):r.getString("name_zh"));
            f.setDescriptionZh(r.getString("description_zh")==null?text.text(f.getDescription(),true):r.getString("description_zh"));
            f.setCategory(r.getString("category"));f.setAudience(r.getString("audience"));f.setPrice(r.getBigDecimal("price"));f.setStock(r.getInt("stock_quantity"));
            f.setThreshold(r.getInt("threshold"));f.setPublished(r.getBoolean("published"));f.setImage(r.getString("garment_image"));f.setSecondImage(r.getString("model_image"));f.setRevision(r.getString("revision"));return f;
        },id);
        if(forms.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var f=forms.get(0);
        f.setOptions(jdbc.query("SELECT * FROM product_variants WHERE product_id=? ORDER BY id",(r,n)->{
            var o=new ProductEditForm.Option();o.setId(r.getLong("id"));o.setSize(r.getString("size_label"));o.setColor(r.getString("color_label"));o.setStock(r.getInt("stock_quantity"));return o;
        },id));
        // Inventory is part of the revision: a checkout after opening the form must not be overwritten.
        String state=f.getRevision()+":"+f.getStock();
        for(var o:f.getOptions()) state+=":"+o.getId()+":"+o.getStock();
        try { f.setRevision(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)))); }
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        return f;
    }
    private String required(String s,int max,String field) {
        if(s==null || s.isBlank() || s.strip().length()>max) throw new IllegalArgumentException(field+": required, maximum "+max+" characters. / 必填且不能超过 "+max+" 个字符。");
        return s.strip();
    }
    private void stock(Integer value) { if(value==null || value<0 || value>1000000) throw new IllegalArgumentException("Stock must be 0–1,000,000. / 库存必须在 0–1,000,000 之间。"); }
    private void imagePath(String path) {
        if(path!=null && !path.isBlank() && (!path.matches("/(images/products/[A-Za-z0-9_./-]+|catalogue-images/[a-f0-9-]{36})") || path.contains("..") || path.length()>255))
            throw new IllegalArgumentException("Upload an image or use a local product image path. / 请上传图片或使用本地商品图片路径。");
    }
    private record Upload(byte[] bytes,String type) {}
    private Upload upload(MultipartFile file) {
        if(file==null || file.isEmpty())return null;
        if(file.getSize()>5*1024*1024)throw new IllegalArgumentException("Image limit: 5 MB. / 单张图片不能超过 5 MB。");
        try(var input=ImageIO.createImageInputStream(file.getInputStream())) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Use a PNG or JPEG image. / 请使用 PNG 或 JPEG 图片。");
            var reader=readers.next();try {
                reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("png","jpeg","jpg").contains(format) || (long)reader.getWidth(0)*reader.getHeight(0)>20000000)throw new IllegalArgumentException("Use PNG/JPEG up to 20 megapixels. / 请使用不超过 2000 万像素的 PNG/JPEG。");
                if(reader.read(0)==null)throw new IllegalArgumentException("Invalid image. / 图片无效。");
                return new Upload(file.getBytes(),format.equals("png")?"image/png":"image/jpeg");
            } finally {reader.dispose();}
        } catch(java.io.IOException ex){throw new IllegalArgumentException("Cannot read image. / 无法读取图片。");}
    }
    private String saveImage(Upload image,String previous) {
        if(image==null)return previous==null?"":previous.strip();
        String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO catalogue_images(id,content_type,image_data) VALUES (?,?,?)",id,image.type(),image.bytes());return "/catalogue-images/"+id;
    }
    public long save(ProductEditForm f,MultipartFile first,MultipartFile second) {
        ready();
        f.setName(required(f.getName(),150,"English name / 英文名称"));f.setNameZh(required(f.getNameZh(),150,"Chinese name / 中文名称"));
        f.setDescription(required(f.getDescription(),10000,"English description / 英文描述"));f.setDescriptionZh(required(f.getDescriptionZh(),10000,"Chinese description / 中文描述"));
        if(!Set.of("Clothing","Household").contains(f.getCategory()) || !Set.of("Men","Women","Unisex","Home").contains(f.getAudience()))throw new IllegalArgumentException("Invalid category or collection. / 分类或系列无效。");
        if(f.getPrice()==null || f.getPrice().signum()<0 || f.getPrice().compareTo(new BigDecimal("99999999.99"))>0 || f.getPrice().stripTrailingZeros().scale()>2)throw new IllegalArgumentException("Enter a valid price with at most two decimals. / 价格需非负且最多两位小数。");
        stock(f.getStock());stock(f.getThreshold());imagePath(f.getImage());imagePath(f.getSecondImage());
        if(f.getOptions()==null || f.getOptions().size()>100)throw new IllegalArgumentException("Maximum 100 options. / 最多 100 个规格。");
        f.getOptions().removeIf(o->o.getId()==null && (o.getSize()==null || o.getSize().isBlank()) && (o.getColor()==null || o.getColor().isBlank()) && (o.getStock()==null || o.getStock()==0));
        Set<String> labels=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(var o:f.getOptions()) {
            o.setSize(required(o.getSize(),30,"Size / 尺码"));o.setColor(required(o.getColor(),60,"Colour / 颜色"));stock(o.getStock());
            if(!labels.add((o.getSize()+"\0"+o.getColor()).toLowerCase(Locale.ROOT)) || (o.getId()!=null && !ids.add(o.getId())))throw new IllegalArgumentException("Duplicate option. / 规格不能重复。");
        }
        if(f.getCategory().equals("Clothing") && f.getOptions().isEmpty())throw new IllegalArgumentException("Clothing needs at least one size/colour option. / 服装至少需要一个尺码颜色规格。");
        Upload a=upload(first),b=upload(second);
        return tx.execute(status->{
            Long id=f.getId();ProductEditForm old=null;
            if(id!=null) {
                jdbc.queryForList("SELECT id FROM products WHERE id=? FOR UPDATE",id);old=edit(id);
                if(!old.getRevision().equals(f.getRevision()))throw new IllegalArgumentException("This product or stock changed. Reopen the editor before saving. / 商品或库存已变化，请重新打开编辑页面后保存。");
                if(old.getOptions().isEmpty() && !f.getOptions().isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id=?",Integer.class,id)>0)
                    throw new IllegalArgumentException("This product has orders without options. Keep its inventory type or create a separate product. / 该商品已有无规格订单，请保留原库存类型或另建商品。");
                Set<Long> oldIds=new HashSet<>();for(var o:old.getOptions())oldIds.add(o.getId());
                if(!oldIds.equals(ids))throw new IllegalArgumentException("Existing options must be retained; set unused stock to zero. / 请保留已有规格，不再使用的规格可将库存设为零。");
                for(var o:old.getOptions())for(var edited:f.getOptions())if(o.getId().equals(edited.getId()) && (!o.getSize().equals(edited.getSize()) || !o.getColor().equals(edited.getColor())))throw new IllegalArgumentException("Existing option labels cannot change; add a new option. / 已有规格名称不能改变，请新增规格。");
                jdbc.update("UPDATE products SET name=?,category=?,description=?,price=? WHERE id=?",f.getName(),f.getCategory(),f.getDescription(),f.getPrice(),id);
            } else {
                if(!ids.isEmpty())throw new IllegalArgumentException("Invalid option IDs. / 规格编号无效。");
                var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO products(name,category,description,price) VALUES (?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);ps.setString(1,f.getName());ps.setString(2,f.getCategory());ps.setString(3,f.getDescription());ps.setBigDecimal(4,f.getPrice());return ps;},key);id=key.getKey().longValue();
            }
            int total=f.getOptions().isEmpty()?f.getStock():f.getOptions().stream().mapToInt(ProductEditForm.Option::getStock).sum();stock(total);
            String image=saveImage(a,f.getImage()),secondImage=saveImage(b,f.getSecondImage());
            if(f.getPublished() && image.isBlank())throw new IllegalArgumentException("Add a main image before publishing. / 上架前请添加主图。");
            int changed=jdbc.update("UPDATE product_details SET audience=?,stock_quantity=?,garment_image=?,model_image=? WHERE product_id=?",f.getAudience(),total,image,secondImage,id);
            if(changed==0)jdbc.update("INSERT INTO product_details(product_id,audience,stock_quantity,garment_image,model_image) VALUES (?,?,?,?,?)",id,f.getAudience(),total,image,secondImage);
            for(var o:f.getOptions()) {
                if(o.getId()==null)jdbc.update("INSERT INTO product_variants(product_id,size_label,color_label,stock_quantity) VALUES (?,?,?,?)",id,o.getSize(),o.getColor(),o.getStock());
                else jdbc.update("UPDATE product_variants SET stock_quantity=? WHERE id=? AND product_id=?",o.getStock(),o.getId(),id);
            }
            changed=jdbc.update("UPDATE product_management SET name_zh=?,description_zh=?,published=?,low_stock_threshold=?,revision=revision+1 WHERE product_id=?",f.getNameZh(),f.getDescriptionZh(),f.getPublished(),f.getThreshold(),id);
            if(changed==0)jdbc.update("INSERT INTO product_management(product_id,name_zh,description_zh,published,low_stock_threshold,revision) VALUES (?,?,?,?,?,1)",id,f.getNameZh(),f.getDescriptionZh(),f.getPublished(),f.getThreshold());
            return id;
        });
    }
    /** Update only inventory, preserving all catalogue fields and checking the displayed revision. */
    public void updateStock(long id, String revision, Integer quantity, Map<Long,Integer> options) {
        ready();
        tx.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM products WHERE id=? FOR UPDATE",id);
            var current=edit(id);
            if(!current.getRevision().equals(revision)) throw new IllegalArgumentException("Stock changed. Reload before saving. / 库存已变化，请重新加载后保存。");
            if(current.getOptions().isEmpty()) {
                if(!options.isEmpty()) throw new IllegalArgumentException("Invalid options. / 规格无效。");
                stock(quantity); current.setStock(quantity);
            } else {
                var ids=new HashSet<Long>();
                for(var option:current.getOptions()) ids.add(option.getId());
                if(!ids.equals(options.keySet())) throw new IllegalArgumentException("Reload all options. / 请重新加载完整规格。");
                for(var option:current.getOptions()) { stock(options.get(option.getId())); option.setStock(options.get(option.getId())); }
            }
            save(current,null,null);
        });
    }
    public record Image(byte[] bytes,String type) {}
    public Optional<Image> image(String id) { return jdbc==null?Optional.empty():jdbc.query("SELECT image_data,content_type FROM catalogue_images WHERE id=?",(r,n)->new Image(r.getBytes(1),r.getString(2)),id).stream().findFirst(); }
}
