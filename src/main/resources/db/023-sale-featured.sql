-- Explicit featured selections; existing campaigns remain intact and can be edited by admins.
CREATE TABLE IF NOT EXISTS sale_campaign_featured (
    campaign_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (campaign_id, product_id),
    FOREIGN KEY (campaign_id, product_id)
        REFERENCES sale_campaign_products(campaign_id, product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
