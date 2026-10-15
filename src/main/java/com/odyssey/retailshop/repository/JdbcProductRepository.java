package com.odyssey.retailshop.repository;

import com.odyssey.retailshop.model.Product;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the shared catalogue; schema changes are applied by the host explicitly. */
@Repository
@Profile("mysql")
public class JdbcProductRepository implements ProductRepository {
    private final JdbcTemplate jdbc;

    public JdbcProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Product> findAll() {
        return jdbc.query("SELECT id, name, category, description, price FROM products ORDER BY id",
            (row, number) -> new Product(row.getLong("id"), row.getString("name"),
                row.getString("category"), row.getString("description"), row.getBigDecimal("price")));
    }
}
