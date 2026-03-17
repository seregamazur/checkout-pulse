INSERT INTO inventory.products (id, name, base_price) VALUES
('e0e0e0e0-e0e0-4e0e-a0e0-000000000001', 'iPhone 15 Pro', 999.00),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000002', 'AirPods Pro 2', 249.00),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000003', 'MacBook Air M3', 1299.00),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000004', 'Kindle Paperwhite', 139.00),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000005', 'Mechanical Keyboard', 150.00);

INSERT INTO inventory.inventory_items (product_id, available_quantity, version) VALUES
('e0e0e0e0-e0e0-4e0e-a0e0-000000000001', 50, 0),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000002', 120, 0),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000003', 15, 0),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000004', 200, 0),
('e0e0e0e0-e0e0-4e0e-a0e0-000000000005', 0, 0);

INSERT INTO inventory.carts (id, user_id) VALUES ('e0e0e0e0-e0e0-4e0e-a0e0-000000000001', 'e0e0e0e0-e0e0-4e0e-a0e0-000000000002');

INSERT INTO inventory.cart_items (id, cart_id, product_id, quantity)
VALUES ('e0e0e0e0-e0e0-4e0e-a0e0-000000000001', 'e0e0e0e0-e0e0-4e0e-a0e0-000000000001', 'e0e0e0e0-e0e0-4e0e-a0e0-000000000001', 2);
INSERT INTO inventory.cart_items (id, cart_id, product_id, quantity)
VALUES ('e0e0e0e0-e0e0-4e0e-a0e0-000000000002', 'e0e0e0e0-e0e0-4e0e-a0e0-000000000001', 'e0e0e0e0-e0e0-4e0e-a0e0-000000000002', 1);
