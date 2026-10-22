-- Payment snapshots are independent of fulfilment status. Existing orders remain unchanged.
CREATE TABLE IF NOT EXISTS order_payments (
 order_id VARCHAR(36) PRIMARY KEY,
 method VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL,
 amount DECIMAL(14,2) NOT NULL,
 payment_entry_id BIGINT UNIQUE,
 refund_entry_id BIGINT UNIQUE,
 paid_at TIMESTAMP NULL,
 refunded_at TIMESTAMP NULL,
 FOREIGN KEY(order_id) REFERENCES shop_orders(id),
 FOREIGN KEY(payment_entry_id) REFERENCES wallet_entries(id),
 FOREIGN KEY(refund_entry_id) REFERENCES wallet_entries(id),
 CHECK(amount>=0),
 CHECK(method IN ('WALLET','OFFLINE')),
 CHECK(status IN ('PAID','REFUNDED','UNPAID','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
