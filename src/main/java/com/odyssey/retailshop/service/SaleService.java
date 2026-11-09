package com.odyssey.retailshop.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SaleService {
    private static final Prices DEMO_PRICES = new Prices(Map.of(
        1L, new Discount("", 25), 2L, new Discount("", 50),
        3L, new Discount("", 20), 4L, new Discount("", 35)
    ));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public SaleService(ObjectProvider<JdbcTemplate> databases, ObjectProvider<PlatformTransactionManager> managers) {
        jdbc = databases.getIfAvailable();
        var manager = managers.getIfAvailable();
        transactions = manager == null ? null : new TransactionTemplate(manager);
    }

    public record Discount(String title, int percent) {
        public BigDecimal price(BigDecimal original) {
            return original.multiply(BigDecimal.valueOf(100L - percent, 2)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record Prices(Map<Long, Discount> discounts) {
        public BigDecimal price(long productId, BigDecimal original) {
            var discount = discounts.get(productId);
            return discount == null ? original : discount.price(original);
        }
    }

    public record Campaign(long id, String title, int percent, LocalDateTime starts, LocalDateTime ends,
                           boolean enabled, int revision) {
        public String status(LocalDateTime now) {
            if (!enabled) return "disabled";
            if (now.isBefore(starts)) return "upcoming";
            return now.isBefore(ends) ? "active" : "ended";
        }
    }

    public boolean available() {
        return jdbc != null && transactions != null;
    }

    public Prices activePrices() {
        if (!available()) return DEMO_PRICES;
        var discounts = new LinkedHashMap<Long, Discount>();
        LocalDateTime now = LocalDateTime.now();
        jdbc.query("""
            SELECT p.product_id, c.title, c.percent_off
            FROM sale_campaigns c JOIN sale_campaign_products p ON p.campaign_id = c.id
            WHERE c.enabled = TRUE AND c.starts_at <= ? AND c.ends_at > ?
            ORDER BY c.percent_off DESC, c.id DESC, p.product_id
            """, row -> {
                discounts.putIfAbsent(row.getLong("product_id"), new Discount(row.getString("title"), row.getInt("percent_off")));
            }, now, now);
        return new Prices(discounts);
    }

    public List<Campaign> activeCampaigns() {
        if (!available()) return List.of();
        var now = LocalDateTime.now();
        return jdbc.query("SELECT * FROM sale_campaigns WHERE enabled = TRUE AND starts_at <= ? AND ends_at > ? ORDER BY id",
            this::readCampaign, now, now);
    }

    public Set<Long> featuredProductIds(long id) {
        return Set.copyOf(jdbc.queryForList("SELECT product_id FROM sale_campaign_featured WHERE campaign_id = ?", Long.class, id));
    }

    public List<Campaign> campaigns() {
        return jdbc.query("SELECT * FROM sale_campaigns ORDER BY id DESC", this::readCampaign);
    }

    public Campaign campaign(long id) {
        return jdbc.query("SELECT * FROM sale_campaigns WHERE id = ?", this::readCampaign, id).stream()
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Campaign not found. / 未找到活动。"));
    }

    public List<Long> productIds(long id) {
        return jdbc.queryForList("SELECT product_id FROM sale_campaign_products WHERE campaign_id = ? ORDER BY product_id",
            Long.class, id);
    }

    public long save(Long id, int revision, String title, int percent, LocalDateTime starts, LocalDateTime ends,
                     boolean enabled, Set<Long> productIds) {
        return save(id, revision, title, percent, starts, ends, enabled, productIds, Set.of());
    }

    public long save(Long id, int revision, String title, int percent, LocalDateTime starts, LocalDateTime ends,
                     boolean enabled, Set<Long> productIds, Set<Long> featuredIds) {
        if (!available()) throw new IllegalArgumentException("Database mode required. / 需要数据库模式。");
        if (!productIds.containsAll(featuredIds)) {
            throw new IllegalArgumentException("Featured products must join this campaign. / 主推商品必须是本活动的参与商品。");
        }
        String cleanTitle = title.strip();
        if (cleanTitle.isEmpty() || cleanTitle.length() > 100 || percent < 1 || percent > 99 || !ends.isAfter(starts)) {
            throw new IllegalArgumentException("Check the title, dates and discount (1–99%). / 请检查名称、起止时间和折扣（1–99%）。");
        }
        if (productIds.isEmpty() || productIds.size() > 500) {
            throw new IllegalArgumentException("Select 1–500 products. / 请选择 1–500 件商品。");
        }
        return transactions.execute(status -> {
            var existing = new NamedParameterJdbcTemplate(jdbc).queryForList(
                "SELECT id FROM products WHERE id IN (:ids)", Map.of("ids", productIds), Long.class);
            if (existing.size() != productIds.size()) {
                throw new IllegalArgumentException("Some products no longer exist. / 部分商品已不存在。");
            }
            long campaignId;
            if (id == null) {
                var key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement("""
                        INSERT INTO sale_campaigns(title, percent_off, starts_at, ends_at, enabled) VALUES (?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, cleanTitle);
                    statement.setInt(2, percent);
                    statement.setObject(3, starts);
                    statement.setObject(4, ends);
                    statement.setBoolean(5, enabled);
                    return statement;
                }, key);
                campaignId = key.getKey().longValue();
            } else {
                int changed = jdbc.update("""
                    UPDATE sale_campaigns SET title = ?, percent_off = ?, starts_at = ?, ends_at = ?, enabled = ?,
                    revision = revision + 1 WHERE id = ? AND revision = ?
                    """, cleanTitle, percent, starts, ends, enabled, id, revision);
                if (changed != 1) {
                    throw new IllegalArgumentException("Campaign has changed. Reopen it before saving. / 活动已变更，请重新打开再保存。");
                }
                campaignId = id;
                jdbc.update("DELETE FROM sale_campaign_products WHERE campaign_id = ?", id);
            }
            List<Object[]> assignments = productIds.stream().sorted().map(product -> new Object[]{campaignId, product}).toList();
            jdbc.batchUpdate("INSERT INTO sale_campaign_products(campaign_id, product_id) VALUES (?, ?)", assignments);
            if (!featuredIds.isEmpty()) {
                jdbc.batchUpdate("INSERT INTO sale_campaign_featured(campaign_id, product_id) VALUES (?, ?)",
                    featuredIds.stream().sorted().map(product -> new Object[]{campaignId, product}).toList());
            }
            return campaignId;
        });
    }

    void saveOrderDiscount(String orderId, BigDecimal discount) {
        if (discount.signum() > 0) {
            jdbc.update("INSERT INTO order_sale_totals(order_id, discount_total) VALUES (?, ?)", orderId, discount);
        }
    }

    public BigDecimal orderDiscount(String orderId) {
        return jdbc.queryForList("SELECT discount_total FROM order_sale_totals WHERE order_id = ?", BigDecimal.class, orderId)
            .stream().findFirst().orElse(BigDecimal.ZERO);
    }

    private Campaign readCampaign(ResultSet row, int number) throws SQLException {
        return new Campaign(row.getLong("id"), row.getString("title"), row.getInt("percent_off"),
            row.getObject("starts_at", LocalDateTime.class), row.getObject("ends_at", LocalDateTime.class),
            row.getBoolean("enabled"), row.getInt("revision"));
    }
}
