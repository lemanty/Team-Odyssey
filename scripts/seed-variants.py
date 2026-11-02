#!/usr/bin/env python3
"""Split existing demo clothing stock across options once; never reset existing SKU stock."""
import database

def main():
    # Eight options receive disjoint portions of the existing available stock.
    statements = ['START TRANSACTION;', "SELECT id FROM products WHERE category='Clothing' ORDER BY id FOR UPDATE;"]
    statements += ["CREATE TEMPORARY TABLE variant_seed_products AS SELECT p.id FROM products p WHERE p.category='Clothing' AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.id);"]
    for index, (size, color) in enumerate((s, c) for c in ['Original colour', 'Charcoal'] for s in ['S', 'M', 'L', 'XL']):
        statements.append(f"INSERT INTO product_variants(product_id,size_label,color_label,stock_quantity) SELECT p.id,'{size}','{color}',FLOOR(d.stock_quantity/8)+CASE WHEN MOD(d.stock_quantity,8)>{index} THEN 1 ELSE 0 END FROM products p JOIN product_details d ON d.product_id=p.id WHERE p.id IN (SELECT id FROM variant_seed_products);")
    statements.append('COMMIT;')
    database.sql('\n'.join(statements))

if __name__ == '__main__':
    main()
