-- Host applies this explicitly. Safe to repeat: no deletion or sample-data reset.
CREATE TABLE IF NOT EXISTS products (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    CONSTRAINT products_price_nonnegative CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
