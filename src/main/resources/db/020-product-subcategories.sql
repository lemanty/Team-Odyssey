-- Two-level catalogue: top category (Men/Women/Household) plus a subcategory column. Safe to repeat.
SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'subcategory'
);
SET @statement := IF(@column_exists = 0,
  'ALTER TABLE products ADD COLUMN subcategory VARCHAR(50) NOT NULL DEFAULT '''' AFTER category',
  'DO 0');
PREPARE add_subcategory FROM @statement;
EXECUTE add_subcategory;
DEALLOCATE PREPARE add_subcategory;