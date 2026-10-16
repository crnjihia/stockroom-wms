-- Flyway Migration V2: Nairobi FMCG Depot Seed Data

-- 1. Users (password is 'password123' hashed with BCrypt)
-- Hash: $2a$10$SmjVGbP5NAAklheoSR7RtOc1hg7pRK88Cbz1yG00rxE255nJKFioW
INSERT INTO users (username, password, full_name, email, role, enabled)
VALUES
('admin', '$2a$10$SmjVGbP5NAAklheoSR7RtOc1hg7pRK88Cbz1yG00rxE255nJKFioW', 'Mwangi Kamau (System Admin)', 'admin@stockroom.co.ke', 'ADMIN', true),
('manager', '$2a$10$SmjVGbP5NAAklheoSR7RtOc1hg7pRK88Cbz1yG00rxE255nJKFioW', 'Wanjiku Njoroge (Depot Manager)', 'manager@stockroom.co.ke', 'WAREHOUSE_MANAGER', true),
('picker', '$2a$10$SmjVGbP5NAAklheoSR7RtOc1hg7pRK88Cbz1yG00rxE255nJKFioW', 'Otieno Ochieng (Warehouse Picker)', 'picker@stockroom.co.ke', 'PICKER', true),
('auditor', '$2a$10$SmjVGbP5NAAklheoSR7RtOc1hg7pRK88Cbz1yG00rxE255nJKFioW', 'Amina Hassan (Internal Auditor)', 'auditor@stockroom.co.ke', 'AUDITOR', true)
ON CONFLICT (username) DO NOTHING;

-- 2. Warehouses (Nairobi FMCG Distribution Depots)
INSERT INTO warehouses (id, code, name, county, town, address, manager_id, active)
VALUES
(1, 'NBO-01', 'Nairobi Central Industrial Area Depot', 'Nairobi', 'Industrial Area', 'Commercial Street, Off Enterprise Road, Nairobi', 2, true),
(2, 'NBO-02', 'Embakasi Logistics Hub & Fulfillment Center', 'Nairobi', 'Embakasi', 'Airport North Road, Near ICD, Nairobi', 2, true),
(3, 'NBO-03', 'Baba Dogo Distribution Depot', 'Nairobi', 'Ruaraka', 'Baba Dogo Road, Ruaraka Light Industrial, Nairobi', 2, true)
ON CONFLICT (code) DO NOTHING;

-- 3. Locations (Bins)
INSERT INTO locations (id, warehouse_id, code, zone, capacity_units)
VALUES
-- NBO-01 Locations
(1, 1, 'REC-01', 'RECEIVING', 5000),
(2, 1, 'A-01-01', 'STORAGE', 2500),
(3, 1, 'A-01-02', 'STORAGE', 2500),
(4, 1, 'A-01-03', 'STORAGE', 2500),
(5, 1, 'PCK-01', 'PICKING', 1000),
(6, 1, 'SHP-01', 'SHIPPING', 3000),
(7, 1, 'RET-01', 'RETURNS', 1500),

-- NBO-02 Locations
(8, 2, 'REC-02', 'RECEIVING', 6000),
(9, 2, 'B-01-01', 'STORAGE', 3000),
(10, 2, 'PCK-02', 'PICKING', 1200),
(11, 2, 'SHP-02', 'SHIPPING', 3500),
(12, 2, 'RET-02', 'RETURNS', 1500),

-- NBO-03 Locations
(13, 3, 'REC-03', 'RECEIVING', 4000),
(14, 3, 'C-01-01', 'STORAGE', 2000),
(15, 3, 'PCK-03', 'PICKING', 800),
(16, 3, 'SHP-03', 'SHIPPING', 2500),
(17, 3, 'RET-03', 'RETURNS', 1000)
ON CONFLICT (warehouse_id, code) DO NOTHING;

-- 4. Products (Kenyan FMCG Fast-Movers)
INSERT INTO products (id, sku, name, description, category, unit_of_measure, barcode, reorder_point, reorder_qty, unit_cost, unit_price)
VALUES
(1, 'PMB-MZ-2KG', 'Pembe Maize Flour 2kg', 'Grade 1 fortified maize flour bale unit', 'Dry Foods', 'BALE', '06161101234566', 150, 500, 195.00, 230.00),
(2, 'JGO-MZ-2KG', 'Jogoo Maize Flour 2kg', 'Premium quality sifted maize meal 2kg pack', 'Dry Foods', 'BALE', '06161102345674', 200, 600, 205.00, 240.00),
(3, 'RNA-OIL-5L', 'Rina Vegetable Cooking Oil 5L', 'Refined palm cooking oil 5 litre jerrican', 'Edible Oils', 'JERRICAN', '06161103456782', 80, 250, 1150.00, 1320.00),
(4, 'KTP-TEA-100S', 'Ketepa Chai Gold Tea Bags 100s', 'Pure Kenya black tea envelope bags pack', 'Beverages', 'CARTON', '06161104567890', 50, 150, 310.00, 380.00),
(5, 'BKS-MLK-500M', 'Brookside Dairy Fresh Milk 500ml', 'Pasteurized homogenized whole milk pouch', 'Dairy', 'CRATE', '06161105678908', 300, 1000, 55.00, 65.00),
(6, 'OMO-DET-1KG', 'Omo Hand Washing Powder 1kg', 'Fast action multi-active laundry detergent pack', 'Home Care', 'CARTON', '06161106789016', 100, 300, 280.00, 340.00)
ON CONFLICT (sku) DO NOTHING;

-- Reset Sequences to avoid id collision on future inserts
SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));
SELECT setval('warehouses_id_seq', (SELECT COALESCE(MAX(id), 1) FROM warehouses));
SELECT setval('locations_id_seq', (SELECT COALESCE(MAX(id), 1) FROM locations));
SELECT setval('products_id_seq', (SELECT COALESCE(MAX(id), 1) FROM products));

-- 5. Seed Initial Stock & Corresponding Ledger Movements (Ensuring Invariant:OnHand == SUM(movements))
-- Stock Item 1: Pembe Maize Flour in NBO-01 Storage A-01-01
INSERT INTO stock_items (id, product_id, location_id, quantity_on_hand, quantity_reserved, batch_number, expiry_date, version)
VALUES (1, 1, 2, 450, 0, 'BAT-PMB-2026A', '2027-06-30', 0)
ON CONFLICT DO NOTHING;

INSERT INTO stock_movements (product_id, location_id, type, quantity, reference_type, reference_id, user_id, notes)
VALUES (1, 2, 'RECEIPT', 450, 'INITIAL_INVENTORY', 'PO-NBO-001', 'admin', 'Initial stock take intake');

-- Stock Item 2: Jogoo Maize Flour in NBO-01 Storage A-01-02
INSERT INTO stock_items (id, product_id, location_id, quantity_on_hand, quantity_reserved, batch_number, expiry_date, version)
VALUES (2, 2, 3, 300, 0, 'BAT-JGO-2026A', '2027-07-15', 0)
ON CONFLICT DO NOTHING;

INSERT INTO stock_movements (product_id, location_id, type, quantity, reference_type, reference_id, user_id, notes)
VALUES (2, 3, 'RECEIPT', 300, 'INITIAL_INVENTORY', 'PO-NBO-002', 'admin', 'Initial stock take intake');

-- Stock Item 3: Rina Cooking Oil in NBO-01 Storage A-01-03
INSERT INTO stock_items (id, product_id, location_id, quantity_on_hand, quantity_reserved, batch_number, expiry_date, version)
VALUES (3, 3, 4, 120, 0, 'BAT-RNA-2026B', '2027-12-31', 0)
ON CONFLICT DO NOTHING;

INSERT INTO stock_movements (product_id, location_id, type, quantity, reference_type, reference_id, user_id, notes)
VALUES (3, 4, 'RECEIPT', 120, 'INITIAL_INVENTORY', 'PO-NBO-003', 'admin', 'Initial stock take intake');

-- Stock Item 4: Low stock item to demonstrate Reorder Alert (Ketepa Chai in NBO-02 Storage B-01-01: Qty 35 <= ReorderPoint 50)
INSERT INTO stock_items (id, product_id, location_id, quantity_on_hand, quantity_reserved, batch_number, expiry_date, version)
VALUES (4, 4, 9, 35, 0, 'BAT-KTP-2026A', '2027-09-30', 0)
ON CONFLICT DO NOTHING;

INSERT INTO stock_movements (product_id, location_id, type, quantity, reference_type, reference_id, user_id, notes)
VALUES (4, 9, 'RECEIPT', 35, 'INITIAL_INVENTORY', 'PO-NBO-004', 'admin', 'Initial low stock level for alert demo');

-- Stock Item 5: Brookside Milk in NBO-03 Storage C-01-01
INSERT INTO stock_items (id, product_id, location_id, quantity_on_hand, quantity_reserved, batch_number, expiry_date, version)
VALUES (5, 5, 14, 800, 0, 'BAT-BKS-2026C', '2026-11-30', 0)
ON CONFLICT DO NOTHING;

INSERT INTO stock_movements (product_id, location_id, type, quantity, reference_type, reference_id, user_id, notes)
VALUES (5, 14, 'RECEIPT', 800, 'INITIAL_INVENTORY', 'PO-NBO-005', 'admin', 'Initial fresh dairy intake');

SELECT setval('stock_items_id_seq', (SELECT COALESCE(MAX(id), 1) FROM stock_items));
SELECT setval('stock_movements_id_seq', (SELECT COALESCE(MAX(id), 1) FROM stock_movements));
