package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import com.odyssey.retailshop.model.ProductDraft;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class JdbcProductRepository implements ProductRepository {
    private static final String SELECT_PRODUCTS = """
        SELECT p.id, p.name, p.category, p.description, p.price, p.active,
               d.audience, d.stock_quantity, d.garment_image, d.model_image
        FROM products p LEFT JOIN product_details d ON d.product_id = p.id
        """;
    private static final RowMapper<Product> PRODUCT_ROW = (row, number) -> new Product(
        row.getLong("id"), row.getString("name"), row.getString("category"),
        row.getString("description"), row.getBigDecimal("price"),
        blankToNull(row.getString("audience")),
        row.getObject("stock_quantity", Integer.class),
        blankToNull(row.getString("garment_image")),
        blankToNull(row.getString("model_image")),
        row.getBoolean("active"));
    private final JdbcTemplate jdbc;

    public JdbcProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public List<Product> findAll() {
        return jdbc.query(SELECT_PRODUCTS + " WHERE p.active = 1 ORDER BY p.category, p.id", PRODUCT_ROW);
    }

    @Override
    public List<Product> findAdminAll() {
        return jdbc.query(SELECT_PRODUCTS + " ORDER BY p.category, p.id", PRODUCT_ROW);
    }

    @Override
    @Transactional
    public long create(ProductDraft draft) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO products(name, category, description, price, active) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, draft.name());
            ps.setString(2, draft.category());
            ps.setString(3, draft.description());
            ps.setBigDecimal(4, draft.price());
            ps.setBoolean(5, draft.active());
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        upsertDetails(id, draft);
        return id;
    }

    @Override
    @Transactional
    public boolean update(long id, ProductDraft draft) {
        int updated = jdbc.update(
            "UPDATE products SET name=?, category=?, description=?, price=?, active=? WHERE id=?",
            draft.name(), draft.category(), draft.description(), draft.price(), draft.active(), id);
        if (updated == 0) return false;
        upsertDetails(id, draft);
        return true;
    }

    private void upsertDetails(long id, ProductDraft draft) {
        jdbc.update("""
            INSERT INTO product_details(product_id, audience, stock_quantity, garment_image, model_image)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              audience = VALUES(audience),
              stock_quantity = VALUES(stock_quantity),
              garment_image = VALUES(garment_image),
              model_image = VALUES(model_image)
            """,
            id,
            draft.audience() == null ? "" : draft.audience(),
            draft.stockQuantity() == null ? 0 : draft.stockQuantity(),
            draft.garmentImage() == null ? "" : draft.garmentImage(),
            draft.modelImage() == null ? "" : draft.modelImage());
    }

    @Override
    @Transactional
    public boolean setActive(long id, boolean active) {
        return jdbc.update("UPDATE products SET active=? WHERE id=?", active, id) > 0;
    }

    @Override
    @Transactional
    public boolean delete(long id) {
        jdbc.update("DELETE FROM product_details WHERE product_id=?", id);
        return jdbc.update("DELETE FROM products WHERE id=?", id) > 0;
    }
}