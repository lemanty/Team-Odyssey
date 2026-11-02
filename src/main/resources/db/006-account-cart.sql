CREATE TABLE IF NOT EXISTS cart_items (
 user_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 PRIMARY KEY(user_id, product_id),
 CONSTRAINT cart_user_fk FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT cart_product_fk FOREIGN KEY(product_id) REFERENCES products(id),
 CONSTRAINT cart_quantity_valid CHECK(quantity BETWEEN 1 AND 999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
