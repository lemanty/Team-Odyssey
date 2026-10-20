CREATE TABLE IF NOT EXISTS order_membership (
 order_id VARCHAR(36) PRIMARY KEY,
 tier VARCHAR(20) NOT NULL,
 percent_payable INT NOT NULL,
 original_total DECIMAL(14,2) NOT NULL,
 discount_total DECIMAL(14,2) NOT NULL,
 CONSTRAINT membership_order_fk FOREIGN KEY(order_id) REFERENCES shop_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
