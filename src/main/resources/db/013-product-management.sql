-- Existing products remain published until explicitly managed. Safe to repeat.
CREATE TABLE IF NOT EXISTS product_management (
 product_id BIGINT PRIMARY KEY,
 name_zh VARCHAR(150) NOT NULL,
 description_zh TEXT NOT NULL,
 published BOOLEAN NOT NULL DEFAULT TRUE,
 low_stock_threshold INT NOT NULL DEFAULT 5,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(product_id) REFERENCES products(id),
 CHECK(low_stock_threshold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS catalogue_images (
 id VARCHAR(36) PRIMARY KEY,
 content_type VARCHAR(30) NOT NULL,
 image_data MEDIUMBLOB NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
