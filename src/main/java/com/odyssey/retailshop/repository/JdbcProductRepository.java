package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class JdbcProductRepository implements ProductRepository {
    private static final String SELECT_PRODUCTS = """
        SELECT p.id, p.name, p.category, p.description, p.price,
               d.audience, d.stock_quantity, d.garment_image, d.model_image, m.name_zh, m.description_zh
        FROM products p LEFT JOIN product_details d ON d.product_id = p.id
        LEFT JOIN product_management m ON m.product_id = p.id
        """;
    private static final RowMapper<Product> PRODUCT_ROW = (row, number) -> new Product(
        row.getLong("id"), row.getString("name"), row.getString("category"),
        row.getString("description"), row.getBigDecimal("price"), row.getString("audience"),
        row.getObject("stock_quantity", Integer.class), row.getString("garment_image"),
        row.getString("model_image")).translated(row.getString("name_zh"), row.getString("description_zh"));
    private final JdbcTemplate jdbc;

    public JdbcProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Product> findAll() {
        return jdbc.query(SELECT_PRODUCTS + " WHERE COALESCE(m.published, TRUE)=TRUE ORDER BY p.category, p.id", PRODUCT_ROW);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jdbc.query(SELECT_PRODUCTS + " WHERE p.id = ? AND COALESCE(m.published, TRUE)=TRUE", PRODUCT_ROW, id).stream().findFirst();
    }
}
