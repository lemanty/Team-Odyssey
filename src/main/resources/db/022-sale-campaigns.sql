CREATE TABLE IF NOT EXISTS sale_campaigns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    percent_off INT NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision INT NOT NULL DEFAULT 0,
    CHECK (percent_off BETWEEN 1 AND 99),
    CHECK (ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sale_campaign_products (
    campaign_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (campaign_id, product_id),
    FOREIGN KEY (campaign_id) REFERENCES sale_campaigns(id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_sale_totals (
    order_id VARCHAR(36) PRIMARY KEY,
    discount_total DECIMAL(14,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES shop_orders(id),
    CHECK (discount_total > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
