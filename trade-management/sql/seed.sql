USE stock_trade_management;

SET NAMES utf8mb4;

-- password: admin123
INSERT INTO admins (username, password_hash, role)
VALUES ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = VALUES(role);

INSERT INTO stocks (stock_code, stock_name, last_price, last_quantity, status, current_limit_rate, next_limit_rate)
VALUES
  ('600519', '贵州茅台', 1688.35, 100, 'TRADING', 0.1000, 0.1000),
  ('000001', '平安银行', 11.50, 500, 'TRADING', 0.1000, 0.1000),
  ('600000', '浦发银行', 8.12, 1000, 'TRADING', 0.1000, 0.1000),
  ('300750', '宁德时代', 186.70, 400, 'TRADING', 0.1000, 0.1000),
  ('002415', '海康威视', 31.86, 800, 'PAUSED', 0.1000, 0.1000)
ON DUPLICATE KEY UPDATE stock_name = VALUES(stock_name);

INSERT INTO admin_stock_permissions (admin_id, stock_code)
SELECT a.id, s.stock_code
FROM admins a
JOIN stocks s
WHERE a.username = 'admin'
ON DUPLICATE KEY UPDATE admin_id = admin_stock_permissions.admin_id;

INSERT INTO trade_orders (order_id, stock_code, side, price, quantity, entered_at, status)
VALUES
  ('B001', '600519', 'BUY', 1688.35, 100, '2026-06-15 10:00:00', 'PENDING'),
  ('B002', '600519', 'BUY', 1687.90, 200, '2026-06-15 10:01:00', 'PENDING'),
  ('S001', '600519', 'SELL', 1689.00, 100, '2026-06-15 10:02:00', 'PENDING'),
  ('S002', '600519', 'SELL', 1690.50, 300, '2026-06-15 10:03:00', 'PENDING')
ON DUPLICATE KEY UPDATE order_id = order_id;

INSERT INTO blacklist (id_card_no, user_name, fund_account_no, security_account_no, reason, active)
VALUES ('330101199001010011', '张三', '6222026000000001', 'A000001', '违规交易风控限制', TRUE)
ON DUPLICATE KEY UPDATE user_name = VALUES(user_name), reason = VALUES(reason), active = TRUE;
