-- Staff aliases are provisioned by the database administrator, not public registration.
CREATE TABLE IF NOT EXISTS user_login_names (
    login_name VARCHAR(80) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    CONSTRAINT login_name_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
