package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SalesReportService {
    private final JdbcTemplate db;

    public SalesReportService(ObjectProvider<JdbcTemplate> database) {
        this.db = database.getIfAvailable();
    }

    public record ProductSales(long id, String name, long orders, long units, BigDecimal gross,
                               long returned, BigDecimal refunds) {}
    public record Report(long orders, long units, BigDecimal sales, BigDecimal refunds,
                         List<ProductSales> products) {
        public BigDecimal net() { return sales.subtract(refunds); }
    }

    public boolean available() { return db != null; }

    public Report read(LocalDate start, LocalDate end) {
        var rows = db.query("""
            SELECT i.product_id, MAX(i.product_name) AS name, COUNT(DISTINCT o.id) AS orders_count,
                   SUM(i.quantity) AS units, SUM(i.quantity*i.unit_price) AS gross,
                   SUM(COALESCE(r.quantity,0)) AS returned, SUM(COALESCE(r.amount,0)) AS refunds,
                   SUM(CASE WHEN i.line_number=1 THEN o.total ELSE 0 END) AS booked,
                   SUM(CASE WHEN i.line_number=1 THEN 1 ELSE 0 END) AS unique_orders
            FROM shop_orders o
            JOIN (SELECT l.*, ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY product_id,variant_id) AS line_number
                  FROM order_all_items l) i ON i.order_id=o.id
            LEFT JOIN (SELECT order_id, product_id, variant_id, SUM(quantity) AS quantity, SUM(refund_amount) AS amount
                       FROM return_requests WHERE status='COMPLETED' GROUP BY order_id,product_id,variant_id) r
              ON r.order_id=i.order_id AND r.product_id=i.product_id AND r.variant_id=i.variant_id
            WHERE o.status='COMPLETED' AND o.created_at>=? AND o.created_at<?
            GROUP BY i.product_id ORDER BY units DESC,i.product_id
            """, (rs,n) -> new Group(new ProductSales(rs.getLong("product_id"),rs.getString("name"),
                rs.getLong("orders_count"),rs.getLong("units"),rs.getBigDecimal("gross"),
                rs.getLong("returned"),rs.getBigDecimal("refunds")),rs.getLong("unique_orders"),
                rs.getBigDecimal("booked")), start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        var products = rows.stream().map(Group::product).toList();
        return new Report(rows.stream().mapToLong(Group::orders).sum(),
            products.stream().mapToLong(ProductSales::units).sum(),
            rows.stream().map(Group::sales).reduce(BigDecimal.ZERO,BigDecimal::add),
            products.stream().map(ProductSales::refunds).reduce(BigDecimal.ZERO,BigDecimal::add), products);
    }
    private record Group(ProductSales product,long orders,BigDecimal sales) {}
}
