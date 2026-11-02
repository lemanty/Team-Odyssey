CREATE TABLE IF NOT EXISTS support_message_extras (
 message_id BIGINT NOT NULL PRIMARY KEY,
 order_id VARCHAR(36) NULL,
 image_data MEDIUMBLOB NULL,
 image_type VARCHAR(32) NULL,
 CONSTRAINT support_extra_message_fk FOREIGN KEY(message_id) REFERENCES support_messages(id),
 CONSTRAINT support_extra_order_fk FOREIGN KEY(order_id) REFERENCES shop_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
