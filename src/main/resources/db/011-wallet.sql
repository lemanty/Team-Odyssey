CREATE TABLE IF NOT EXISTS wallet_entries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 request_token VARCHAR(36) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 balance_after DECIMAL(12,2) NOT NULL,
 note VARCHAR(300) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(actor_id,request_token),
 FOREIGN KEY(user_id) REFERENCES users(id),
 FOREIGN KEY(actor_id) REFERENCES users(id),
 CHECK(balance_after>=0),
 CHECK(amount<>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
