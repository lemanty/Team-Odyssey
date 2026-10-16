-- Available stock is reserved at checkout and restored only on cancellation.
CREATE TABLE IF NOT EXISTS shop_orders (
 id VARCHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 checkout_token VARCHAR(36) NOT NULL,
 status VARCHAR(24) NOT NULL,
 fulfillment VARCHAR(16) NOT NULL,
 address VARCHAR(500) NOT NULL,
 total DECIMAL(14,2) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT order_user_fk FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT checkout_once UNIQUE(user_id, checkout_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS order_items (
 order_id VARCHAR(36) NOT NULL,
 product_id BIGINT NOT NULL,
 product_name VARCHAR(150) NOT NULL,
 unit_price DECIMAL(10,2) NOT NULL,
 quantity INT NOT NULL,
 PRIMARY KEY(order_id,product_id),
 CONSTRAINT item_order_fk FOREIGN KEY(order_id) REFERENCES shop_orders(id),
 CONSTRAINT item_product_fk FOREIGN KEY(product_id) REFERENCES products(id),
 CONSTRAINT item_quantity_positive CHECK(quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
