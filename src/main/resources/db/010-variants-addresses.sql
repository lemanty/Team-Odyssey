CREATE TABLE IF NOT EXISTS product_variants (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL,
 size_label VARCHAR(30) NOT NULL,
 color_label VARCHAR(60) NOT NULL,
 stock_quantity INT NOT NULL,
 CONSTRAINT variant_product_fk FOREIGN KEY(product_id) REFERENCES products(id),
 CONSTRAINT variant_unique UNIQUE(product_id,size_label,color_label),
 CONSTRAINT variant_stock_positive CHECK(stock_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS cart_variants (
 user_id BIGINT NOT NULL,
 variant_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 PRIMARY KEY(user_id,variant_id),
 FOREIGN KEY(user_id) REFERENCES users(id),
 FOREIGN KEY(variant_id) REFERENCES product_variants(id),
 CHECK(quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS order_variant_items (
 order_id VARCHAR(36) NOT NULL,
 variant_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 product_name VARCHAR(150) NOT NULL,
 unit_price DECIMAL(10,2) NOT NULL,
 quantity INT NOT NULL,
 size_label VARCHAR(30) NOT NULL,
 color_label VARCHAR(60) NOT NULL,
 PRIMARY KEY(order_id,variant_id),
 FOREIGN KEY(order_id) REFERENCES shop_orders(id),
 FOREIGN KEY(variant_id) REFERENCES product_variants(id),
 FOREIGN KEY(product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE OR REPLACE VIEW order_all_items AS
 SELECT order_id,product_id,product_name,unit_price,quantity,0 AS variant_id,'' AS size_label,'' AS color_label FROM order_items
 UNION ALL
 SELECT order_id,product_id,product_name,unit_price,quantity,variant_id,size_label,color_label FROM order_variant_items;
CREATE TABLE IF NOT EXISTS user_addresses (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 recipient VARCHAR(80) NOT NULL,
 phone VARCHAR(40) NOT NULL,
 address_line VARCHAR(350) NOT NULL,
 is_default BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
