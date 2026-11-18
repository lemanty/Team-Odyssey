package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsReportService {
    private final JdbcTemplate db;
    public OperationsReportService(ObjectProvider<JdbcTemplate> database) {
        db = database.getIfAvailable();
    }
    public record Table(List<String> columns, List<List<String>> rows) {}
    public boolean available() { return db != null; }

    private Table query(List<String> headings, String sql, Object... parameters) {
        if (db == null) return new Table(headings, List.of());
        return new Table(headings, db.query(sql, (result, index) -> {
            List<String> row = new ArrayList<>();
            for (int column = 1; column <= headings.size(); column++) {
                String value = result.getString(column);
                row.add(value == null ? "" : value);
            }
            return row;
        }, parameters));
    }

    public Table trend(LocalDate start, LocalDate end) {
        Table raw = query(List.of("Date / 日期", "Orders / 订单", "Sales EUR / 折后金额"),
            """
            SELECT CAST(created_at AS DATE), COUNT(*), SUM(total)
            FROM shop_orders WHERE status='COMPLETED' AND created_at>=? AND created_at<?
            GROUP BY CAST(created_at AS DATE) ORDER BY 1
            """, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Map<String,List<String>> dates = new HashMap<>();
        raw.rows().forEach(row -> dates.put(row.get(0), row));
        List<List<String>> rows = new ArrayList<>();
        start.datesUntil(end.plusDays(1)).forEach(day ->
            rows.add(dates.getOrDefault(day.toString(), List.of(day.toString(), "0", "0.00"))));
        return new Table(raw.columns(), rows);
    }

    public Table categories(LocalDate start, LocalDate end) {
        return query(List.of("Category / 当前分类", "Units / 件数", "Before discounts EUR / 折前金额"),
            """
            SELECT p.category, SUM(i.quantity), SUM(i.quantity*i.unit_price)
            FROM order_all_items i JOIN shop_orders o ON o.id=i.order_id
            JOIN products p ON p.id=i.product_id
            WHERE o.status='COMPLETED' AND o.created_at>=? AND o.created_at<?
            GROUP BY p.category ORDER BY 3 DESC
            """, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    public Table products(LocalDate start, LocalDate end, String sort) {
        String order = "slow".equals(sort) ? "units ASC,p.id" : "units DESC,p.id";
        return query(List.of("Product ID / 商品编号", "Product / 商品", "Units / 销量",
            "Before discounts EUR / 折前金额", "Returned units / 退货件数", "Return rate % / 件数退货率"),
            """
            SELECT p.id,p.name,COALESCE(s.units,0) units,COALESCE(s.gross,0),
                   COALESCE(r.returned,0),
                   CASE WHEN COALESCE(s.units,0)=0 THEN 0
                        ELSE ROUND(100.0*COALESCE(r.returned,0)/s.units,2) END
            FROM products p
            LEFT JOIN (
                SELECT i.product_id,SUM(i.quantity) units,SUM(i.quantity*i.unit_price) gross
                FROM order_all_items i JOIN shop_orders o ON o.id=i.order_id
                WHERE o.status='COMPLETED' AND o.created_at>=? AND o.created_at<?
                GROUP BY i.product_id
            ) s ON s.product_id=p.id
            LEFT JOIN (
                SELECT r.product_id,SUM(r.quantity) returned
                FROM return_requests r JOIN shop_orders o ON o.id=r.order_id
                WHERE r.status='COMPLETED' AND o.status='COMPLETED'
                  AND o.created_at>=? AND o.created_at<? GROUP BY r.product_id
            ) r ON r.product_id=p.id ORDER BY
            """ + order, start.atStartOfDay(),end.plusDays(1).atStartOfDay(),
            start.atStartOfDay(),end.plusDays(1).atStartOfDay());
    }

    public Table productOrders(LocalDate start, LocalDate end, long productId) {
        return query(List.of("Order / 订单", "Placed / 下单时间", "Product / 商品",
            "Size / 尺码", "Colour / 颜色", "Units / 件数", "Unit price EUR / 单价"),
            """
            SELECT o.id,o.created_at,i.product_name,i.size_label,i.color_label,i.quantity,i.unit_price
            FROM order_all_items i JOIN shop_orders o ON o.id=i.order_id
            WHERE o.status='COMPLETED' AND i.product_id=? AND o.created_at>=? AND o.created_at<?
            ORDER BY o.created_at DESC,o.id,i.variant_id
            """, productId,start.atStartOfDay(),end.plusDays(1).atStartOfDay());
    }

    public Table inventory(String filter) {
        String condition = switch (filter) {
            case "low" -> " WHERE stock<=threshold";
            case "out" -> " WHERE stock=0";
            default -> "";
        };
        return query(List.of("Product ID / 商品编号","Product / 商品","Option / 规格",
            "Stock / 可售库存","Threshold / 库存预警线","Suggested top-up / 补至预警线以上"),
            """
            SELECT id,name,option_name,stock,threshold,
                CASE WHEN stock<=threshold THEN threshold+1-stock ELSE 0 END
            FROM (
                SELECT p.id,p.name,CONCAT(v.size_label,' / ',v.color_label) option_name,
                    v.stock_quantity stock,COALESCE(m.low_stock_threshold,5) threshold
                FROM products p JOIN product_variants v ON v.product_id=p.id
                LEFT JOIN product_management m ON m.product_id=p.id
                UNION ALL
                SELECT p.id,p.name,'',d.stock_quantity,COALESCE(m.low_stock_threshold,5)
                FROM products p JOIN product_details d ON d.product_id=p.id
                LEFT JOIN product_management m ON m.product_id=p.id
                WHERE NOT EXISTS(SELECT 1 FROM product_variants v WHERE v.product_id=p.id)
            ) inventory
            """ + condition + " ORDER BY stock,id,option_name");
    }

    public Table returns(LocalDate start, LocalDate end, String status) {
        String clause = status.equals("PENDING") ? " AND r.status IN ('REQUESTED','APPROVED','RECEIVED')" : "";
        return query(List.of("Return / 售后编号","Order / 订单","Status / 状态","Reason / 原因",
            "Refund EUR / 已退款","Requested / 申请时间","Updated / 更新时间","Elapsed hours / 处理或等待小时"),
            """
            SELECT r.id,r.order_id,r.status,r.reason,r.refund_amount,r.created_at,r.updated_at,
                TIMESTAMPDIFF(HOUR,r.created_at,
                    CASE WHEN r.status IN ('COMPLETED','REJECTED') THEN r.updated_at ELSE CURRENT_TIMESTAMP END)
            FROM return_requests r WHERE r.created_at>=? AND r.created_at<?
            """ + clause + " ORDER BY r.created_at,r.id",
            start.atStartOfDay(),end.plusDays(1).atStartOfDay());
    }

    public Table reasons(LocalDate start, LocalDate end) {
        return query(List.of("Reason / 顾客填写原因","Requests / 申请数","Completed refunds EUR / 已退款"),
            """
            SELECT reason,COUNT(*),SUM(CASE WHEN status='COMPLETED' THEN refund_amount ELSE 0 END)
            FROM return_requests WHERE created_at>=? AND created_at<?
            GROUP BY reason ORDER BY 2 DESC,reason
            """,start.atStartOfDay(),end.plusDays(1).atStartOfDay());
    }

    public Table orders(LocalDate start, LocalDate end, String status, String fulfillment, String search) {
        if (!Set.of("ALL","PENDING","PREPARING","SHIPPED","READY","COMPLETED","CANCELLED").contains(status)
            || !Set.of("ALL","DELIVERY","COLLECTION").contains(fulfillment)) {
            throw new IllegalArgumentException("Invalid order filter / 订单筛选无效");
        }
        List<Object> args = new ArrayList<>(List.of(start.atStartOfDay(),end.plusDays(1).atStartOfDay()));
        String where = "";
        if (!status.equals("ALL")) { where += " AND o.status=?"; args.add(status); }
        if (!fulfillment.equals("ALL")) { where += " AND o.fulfillment=?"; args.add(fulfillment); }
        if (!search.isBlank()) {
            where += " AND (LOWER(o.id) LIKE ? ESCAPE '!' OR LOWER(u.email) LIKE ? ESCAPE '!' OR LOWER(u.display_name) LIKE ? ESCAPE '!')";
            String term = "%" + search.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_") + "%";
            args.addAll(List.of(term,term,term));
        }
        return query(List.of("Order / 订单","Customer / 顾客","Status / 状态","Fulfillment / 配送方式",
            "Total EUR / 金额","Placed / 下单时间","Elapsed hours / 处理或等待小时"),
            """
            SELECT o.id,u.display_name,o.status,o.fulfillment,o.total,o.created_at,
              TIMESTAMPDIFF(HOUR,o.created_at,
                CASE WHEN o.status IN ('COMPLETED','CANCELLED') THEN o.updated_at ELSE CURRENT_TIMESTAMP END)
            FROM shop_orders o JOIN users u ON u.id=o.user_id
            WHERE o.created_at>=? AND o.created_at<?
            """ + where + " ORDER BY o.created_at DESC,o.id", args.toArray());
    }
}
