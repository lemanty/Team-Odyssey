-- Product-level demonstration stock; not SKU reservations or checkout stock control.
CREATE TABLE IF NOT EXISTS product_details (
    product_id BIGINT NOT NULL PRIMARY KEY,
    audience VARCHAR(20) NOT NULL,
    stock_quantity INT NOT NULL,
    garment_image VARCHAR(255) NOT NULL,
    model_image VARCHAR(255) NOT NULL,
    CONSTRAINT details_product_fk FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT details_stock_nonnegative CHECK (stock_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
