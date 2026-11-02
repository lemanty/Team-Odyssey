-- Catalogue availability flag: an inactive product is hidden from the public catalogue
-- but remains visible in the staff/admin workspace until it is permanently deleted.
-- Safe to repeat: the column is added only when it does not already exist.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'products'
      AND COLUMN_NAME = 'active'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE products ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;