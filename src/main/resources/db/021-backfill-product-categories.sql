-- Upgrade existing catalogue rows in place. The sample mapping is frozen at migration 021.
CREATE TEMPORARY TABLE category_samples_021 (
    name VARCHAR(150) PRIMARY KEY, category VARCHAR(50), subcategory VARCHAR(50)
) CHARACTER SET utf8mb4;
INSERT INTO category_samples_021 VALUES
    ('Everyday Cotton Tee', 'Men', 'tops'),
    ('Linen Blend Shirt', 'Men', 'shirts'),
    ('Classic Crewneck Sweatshirt', 'Men', 'knitwear'),
    ('Straight Leg Jeans', 'Men', 'trousers'),
    ('Canvas Overshirt', 'Men', 'outerwear'),
    ('Soft Knit Cardigan', 'Women', 'knitwear'),
    ('Harbour Stripe Tee', 'Men', 'tops'),
    ('Relaxed Chino Trousers', 'Men', 'trousers'),
    ('Merino Crewneck Jumper', 'Men', 'knitwear'),
    ('Weekend Zip Jacket', 'Men', 'outerwear'),
    ('Oxford Button-Down Shirt', 'Men', 'shirts'),
    ('Everyday Ribbed Tee', 'Women', 'tops'),
    ('Coastal Linen Dress', 'Women', 'skirts-dresses'),
    ('Pleated Midi Skirt', 'Women', 'skirts-dresses'),
    ('Wide-Leg Linen Trousers', 'Women', 'trousers'),
    ('Soft Wrap Blouse', 'Women', 'shirts-blouses'),
    ('Everyday Denim Jacket', 'Women', 'outerwear'),
    ('Fine Knit Polo', 'Women', 'knitwear'),
    ('Belted Trench Coat', 'Women', 'outerwear'),
    ('Relaxed Poplin Shirt', 'Women', 'shirts-blouses'),
    ('Ivory Ceramic Vase', 'Household', 'living-room'),
    ('Seagrass Storage Basket', 'Household', 'storage'),
    ('Sage Linen Cushion', 'Household', 'living-room'),
    ('Oak Serving Tray', 'Household', 'kitchen-dining'),
    ('Sandstone Ceramic Mug', 'Household', 'kitchen-dining'),
    ('Oatmeal Cotton Throw', 'Household', 'bedroom'),
    ('Linen Bedside Lamp', 'Household', 'bedroom'),
    ('Oak Wall Clock', 'Household', 'living-room');

START TRANSACTION;
-- If a previous import already created a second ID, leave both rows for review.
UPDATE products p
LEFT JOIN product_details d ON d.product_id = p.id
LEFT JOIN category_samples_021 s ON s.name = p.name AND s.category IN ('Men','Women')
LEFT JOIN products existing ON existing.name = p.name AND existing.category IN ('Men','Women')
SET p.category = COALESCE(s.category, d.audience),
    p.subcategory = COALESCE(s.subcategory, 'other')
WHERE p.category = 'Clothing' AND existing.id IS NULL
  AND COALESCE(s.category, d.audience) IN ('Men','Women');

-- Also handles partially upgraded catalogues, without overwriting staff selections.
UPDATE products p
LEFT JOIN category_samples_021 s ON s.name = p.name AND s.category = p.category
SET p.subcategory = COALESCE(s.subcategory, 'other')
WHERE p.category IN ('Men','Women','Household') AND p.subcategory = '';
COMMIT;

DROP TEMPORARY TABLE category_samples_021;
-- Ambiguous, unisex or previously duplicated rows remain visible under Browse clothing.
SELECT p.id, p.name, d.audience, existing.id AS possible_duplicate_id
FROM products p
LEFT JOIN product_details d ON d.product_id = p.id
LEFT JOIN products existing ON existing.name = p.name AND existing.category IN ('Men','Women')
WHERE p.category = 'Clothing' ORDER BY p.id, existing.id;
