CREATE TABLE IF NOT EXISTS stock_adjustments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL,
 variant_id BIGINT NOT NULL DEFAULT 0,
 before_quantity INT NOT NULL,
 after_quantity INT NOT NULL,
 actor VARCHAR(254) NOT NULL,
 reason VARCHAR(300) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(product_id) REFERENCES products(id),
 CHECK(before_quantity>=0),
 CHECK(after_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
