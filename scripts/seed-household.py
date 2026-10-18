#!/usr/bin/env python3
"""Explicit, repeatable household sample import. Existing prices and stock are preserved."""
import json
from pathlib import Path
import database
from decimal import Decimal


def literal(value):
    return "CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4)"


def main():
    rows = json.loads((Path(__file__).parent / 'data/household-samples.json').read_text())
    statements = ['START TRANSACTION;']
    for product in rows:
        path = '/images/products/' + product['slug'] + '.png'
        if not (database.ROOT / 'src/main/resources/static' / path.lstrip('/')).is_file():
            raise RuntimeError('Missing product photograph: ' + path)
        name = literal(product['name'])
        price = Decimal(str(product['price']))
        stock = int(product['stock'])
        if price < 0 or stock < 0:
            raise ValueError('Price and stock must be nonnegative')
        statements.append(f"INSERT INTO products(name,category,description,price) SELECT {name},'Household',{literal(product['description'])},{price} WHERE NOT EXISTS (SELECT 1 FROM products WHERE name={name} AND category='Household');")
        image = literal(path)
        statements.append(f"INSERT INTO product_details(product_id,audience,stock_quantity,garment_image,model_image) SELECT id,'Home',{stock},{image},{image} FROM products WHERE name={name} AND category='Household' ON DUPLICATE KEY UPDATE garment_image={image},model_image={image};")
    statements.append('COMMIT;')
    database.sql('\n'.join(statements))
    database.sql("SELECT name,price,stock_quantity FROM products p LEFT JOIN product_details d ON d.product_id=p.id WHERE category='Household';")


if __name__ == '__main__':
    main()
