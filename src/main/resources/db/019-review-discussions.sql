CREATE TABLE IF NOT EXISTS product_review_messages (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 rating INT NOT NULL,
 body VARCHAR(2000) NOT NULL,
 order_id VARCHAR(36),
 variant_id BIGINT NOT NULL DEFAULT 0,
 size_label VARCHAR(30) NOT NULL DEFAULT '',
 color_label VARCHAR(60) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 parent_id BIGINT,
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(parent_id) REFERENCES product_review_messages(id),
 FOREIGN KEY(product_id) REFERENCES products(id),
 FOREIGN KEY(user_id) REFERENCES users(id),
 FOREIGN KEY(order_id) REFERENCES shop_orders(id),
 CHECK(rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS review_upgrade_log (id INT PRIMARY KEY);
INSERT INTO product_review_messages(id,product_id,user_id,rating,body,order_id,variant_id,size_label,color_label,created_at,updated_at)
SELECT id,product_id,user_id,rating,body,order_id,variant_id,size_label,color_label,created_at,updated_at FROM product_reviews
WHERE NOT EXISTS (SELECT 1 FROM review_upgrade_log WHERE id=19);
INSERT INTO review_upgrade_log(id) SELECT 19 WHERE NOT EXISTS (SELECT 1 FROM review_upgrade_log WHERE id=19);
