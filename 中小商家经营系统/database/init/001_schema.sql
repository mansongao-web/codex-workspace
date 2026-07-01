CREATE DATABASE IF NOT EXISTS retail_ops CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE retail_ops;

CREATE TABLE IF NOT EXISTS categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL UNIQUE,
    contact_name VARCHAR(80),
    phone VARCHAR(40),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku VARCHAR(64) NOT NULL UNIQUE,
    barcode VARCHAR(64) UNIQUE,
    name VARCHAR(160) NOT NULL,
    category_id BIGINT,
    supplier_id BIGINT,
    unit VARCHAR(32) NOT NULL DEFAULT 'piece',
    cost_price DECIMAL(18,2) NOT NULL,
    retail_price DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    INDEX idx_products_category_status (category_id, status),
    INDEX idx_products_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS stock_levels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL DEFAULT 1,
    product_sku VARCHAR(64) NOT NULL UNIQUE,
    quantity INT NOT NULL DEFAULT 0,
    min_quantity INT NOT NULL DEFAULT 10,
    max_quantity INT NOT NULL DEFAULT 200,
    location VARCHAR(64) NOT NULL DEFAULT 'MAIN',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_stock_low (store_id, quantity),
    INDEX idx_stock_sku (product_sku)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL DEFAULT 1,
    product_sku VARCHAR(64) NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity_delta INT NOT NULL,
    unit_cost DECIMAL(18,2),
    reference_type VARCHAR(64),
    reference_id VARCHAR(80),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_movements_sku_created (product_sku, created_at),
    INDEX idx_movements_reference (reference_type, reference_id),
    INDEX idx_movements_type_created (movement_type, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inventory_alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL DEFAULT 1,
    product_sku VARCHAR(64) NOT NULL,
    alert_type VARCHAR(32) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(255) NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    INDEX idx_alerts_open (resolved, created_at),
    INDEX idx_alerts_sku (product_sku, resolved)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sales_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL DEFAULT 1,
    order_no VARCHAR(40) NOT NULL UNIQUE,
    business_date DATE NOT NULL,
    subtotal_amount DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    payable_amount DECIMAL(18,2) NOT NULL,
    cost_amount DECIMAL(18,2) NOT NULL,
    profit_amount DECIMAL(18,2) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    cashier VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PAID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_orders_business_date (business_date, status),
    INDEX idx_orders_cashier_date (cashier, business_date),
    INDEX idx_orders_created (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sales_order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    unit_cost DECIMAL(18,2) NOT NULL,
    line_amount DECIMAL(18,2) NOT NULL,
    profit_amount DECIMAL(18,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_items_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    INDEX idx_items_order (order_id),
    INDEX idx_items_sku_created (product_sku, created_at)
) ENGINE=InnoDB;

INSERT INTO categories (id, name) VALUES
    (1, 'Drinks'),
    (2, 'Snacks'),
    (3, 'Daily Goods'),
    (4, 'Instant Food')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO suppliers (id, name, contact_name, phone) VALUES
    (1, 'City Wholesale Market', 'Liu', '13800000001'),
    (2, 'Neighborhood Distributor', 'Chen', '13800000002')
ON DUPLICATE KEY UPDATE name = VALUES(name), contact_name = VALUES(contact_name), phone = VALUES(phone);

INSERT INTO products
    (id, sku, barcode, name, category_id, supplier_id, unit, cost_price, retail_price, status)
VALUES
    (1, 'SKU-WATER-550', '6900000000011', 'Bottled Water 550ml', 1, 1, 'bottle', 0.80, 2.00, 'ACTIVE'),
    (2, 'SKU-COLA-330', '6900000000028', 'Cola 330ml', 1, 1, 'can', 1.80, 3.50, 'ACTIVE'),
    (3, 'SKU-CHIPS-60', '6900000000035', 'Potato Chips 60g', 2, 2, 'bag', 2.20, 5.00, 'ACTIVE'),
    (4, 'SKU-NOODLE-BEEF', '6900000000042', 'Instant Noodles Beef', 4, 1, 'cup', 3.20, 6.00, 'ACTIVE'),
    (5, 'SKU-TISSUE-10', '6900000000059', 'Pocket Tissue 10 Pack', 3, 2, 'pack', 4.50, 8.00, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    barcode = VALUES(barcode),
    name = VALUES(name),
    category_id = VALUES(category_id),
    supplier_id = VALUES(supplier_id),
    unit = VALUES(unit),
    cost_price = VALUES(cost_price),
    retail_price = VALUES(retail_price),
    status = VALUES(status);

INSERT INTO stock_levels (product_sku, quantity, min_quantity, max_quantity, location) VALUES
    ('SKU-WATER-550', 8, 12, 240, 'MAIN'),
    ('SKU-COLA-330', 46, 15, 180, 'MAIN'),
    ('SKU-CHIPS-60', 35, 12, 160, 'MAIN'),
    ('SKU-NOODLE-BEEF', 22, 10, 120, 'MAIN'),
    ('SKU-TISSUE-10', 6, 8, 80, 'MAIN')
ON DUPLICATE KEY UPDATE
    quantity = VALUES(quantity),
    min_quantity = VALUES(min_quantity),
    max_quantity = VALUES(max_quantity),
    location = VALUES(location);

INSERT INTO inventory_alerts (product_sku, alert_type, severity, message, resolved) VALUES
    ('SKU-WATER-550', 'LOW_STOCK', 'MEDIUM', 'Stock for SKU-WATER-550 is 8, below warning threshold 12', FALSE),
    ('SKU-TISSUE-10', 'LOW_STOCK', 'MEDIUM', 'Stock for SKU-TISSUE-10 is 6, below warning threshold 8', FALSE)
ON DUPLICATE KEY UPDATE message = VALUES(message);

INSERT INTO sales_orders
    (order_no, business_date, subtotal_amount, discount_amount, payable_amount, cost_amount,
     profit_amount, payment_method, cashier, status, created_at)
VALUES
    ('SO-DEMO-TODAY-001', CURRENT_DATE, 17.50, 0.50, 17.00, 8.60, 8.40, 'WECHAT', 'demo-cashier', 'PAID', CURRENT_TIMESTAMP),
    ('SO-DEMO-WEEK-001', DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), 13.00, 0.00, 13.00, 6.20, 6.80, 'CASH', 'demo-cashier', 'PAID', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

SET @today_order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-DEMO-TODAY-001');
SET @week_order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-DEMO-WEEK-001');

INSERT INTO sales_order_items
    (order_id, product_sku, product_name, quantity, unit_price, unit_cost, line_amount, profit_amount)
VALUES
    (@today_order_id, 'SKU-COLA-330', 'Cola 330ml', 2, 3.50, 1.80, 7.00, 3.40),
    (@today_order_id, 'SKU-CHIPS-60', 'Potato Chips 60g', 2, 5.00, 2.20, 10.00, 5.60),
    (@week_order_id, 'SKU-WATER-550', 'Bottled Water 550ml', 2, 2.00, 0.80, 4.00, 2.40),
    (@week_order_id, 'SKU-NOODLE-BEEF', 'Instant Noodles Beef', 1, 6.00, 3.20, 6.00, 2.80),
    (@week_order_id, 'SKU-COLA-330', 'Cola 330ml', 1, 3.50, 1.80, 3.50, 1.70);
