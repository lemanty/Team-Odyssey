CREATE TABLE IF NOT EXISTS coupons (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE,
 title VARCHAR(100) NOT NULL,
 kind VARCHAR(10) NOT NULL,
 value_amount DECIMAL(12,2) NOT NULL,
 minimum_total DECIMAL(12,2) NOT NULL,
 starts_at TIMESTAMP NOT NULL,
 ends_at TIMESTAMP NOT NULL,
 stack_membership BOOLEAN NOT NULL DEFAULT FALSE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_by BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(created_by) REFERENCES users(id),
 CHECK(kind IN ('FIXED','PERCENT')),
 CHECK(value_amount>0), CHECK(minimum_total>=0), CHECK(ends_at>starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS order_coupons (
 order_id VARCHAR(36) PRIMARY KEY,
 coupon_id BIGINT NOT NULL,
 code VARCHAR(40) NOT NULL,
 title VARCHAR(100) NOT NULL,
 kind VARCHAR(10) NOT NULL,
 value_amount DECIMAL(12,2) NOT NULL,
 minimum_total DECIMAL(12,2) NOT NULL,
 stack_membership BOOLEAN NOT NULL,
 discount_total DECIMAL(14,2) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES shop_orders(id),
 FOREIGN KEY(coupon_id) REFERENCES coupons(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS coupon_uses (
 coupon_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 order_id VARCHAR(36) NOT NULL UNIQUE,
 PRIMARY KEY(coupon_id,user_id),
 FOREIGN KEY(coupon_id) REFERENCES coupons(id),
 FOREIGN KEY(user_id) REFERENCES users(id),
 FOREIGN KEY(order_id) REFERENCES shop_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
