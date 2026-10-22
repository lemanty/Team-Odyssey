CREATE TABLE IF NOT EXISTS support_return_links (
 message_id BIGINT PRIMARY KEY, return_id VARCHAR(36) NOT NULL,
 FOREIGN KEY(message_id) REFERENCES support_messages(id), FOREIGN KEY(return_id) REFERENCES return_requests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
