CREATE TABLE IF NOT EXISTS support_conversations (
 id VARCHAR(36) PRIMARY KEY,
 customer_id BIGINT NOT NULL UNIQUE,
 last_customer_message BIGINT NOT NULL DEFAULT 0,
 last_staff_message BIGINT NOT NULL DEFAULT 0,
 resolved BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT support_customer_fk FOREIGN KEY(customer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS support_messages (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 conversation_id VARCHAR(36) NOT NULL,
 sender_id BIGINT NOT NULL,
 sender_role VARCHAR(20) NOT NULL,
 body VARCHAR(2000) NOT NULL,
 request_token VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT support_message_thread_fk FOREIGN KEY(conversation_id) REFERENCES support_conversations(id),
 CONSTRAINT support_message_sender_fk FOREIGN KEY(sender_id) REFERENCES users(id),
 CONSTRAINT support_send_once UNIQUE(sender_id, request_token),
 INDEX support_history(conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS support_reads (
 conversation_id VARCHAR(36) NOT NULL,
 user_id BIGINT NOT NULL,
 last_message_id BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(conversation_id,user_id),
 CONSTRAINT support_read_thread_fk FOREIGN KEY(conversation_id) REFERENCES support_conversations(id),
 CONSTRAINT support_read_user_fk FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
