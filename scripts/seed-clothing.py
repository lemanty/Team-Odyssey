#!/usr/bin/env python3
"""Explicit demo catalogue import. Does not run during website startup."""
import json
from pathlib import Path
import database


def literal(value):
    return "CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4)"


def main():
    rows = json.loads((Path(__file__).parent / 'data/clothing-samples.json').read_text())
    statements = ['START TRANSACTION;']
    for p in rows:
        name = literal(p['name'])
        description = literal(p['description'])
        category = literal(p['gender'])
        subcategory = literal(p['subcategory'])
        statements.append(f"INSERT INTO products(name,category,subcategory,description,price) SELECT {name},{category},{subcategory},{description},{p['price']} WHERE NOT EXISTS (SELECT 1 FROM products WHERE name={name} AND category={category});")
        garment = literal('/images/products/' + p['slug'] + '-product.webp')
        model = literal('/images/products/' + p['slug'] + '-model-v2.webp')
        # Preserve existing stock on rerun; refresh only image paths and audience.
        statements.append(f"INSERT INTO product_details(product_id,audience,stock_quantity,garment_image,model_image) SELECT id,{literal(p['gender'])},{p['stock']},{garment},{model} FROM products WHERE name={name} AND category={category} ON DUPLICATE KEY UPDATE audience={literal(p['gender'])},garment_image={garment},model_image={model};")
    statements.append('COMMIT;')
    database.sql('\n'.join(statements))
    database.sql("SELECT COUNT(*) AS clothing_total FROM products WHERE category IN ('Men','Women');")


if __name__ == '__main__':
    main()
