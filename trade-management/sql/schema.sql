CREATE DATABASE IF NOT EXISTS stock_trade_management
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE stock_trade_management;

CREATE TABLE IF NOT EXISTS admins (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stocks (
  stock_code CHAR(6) PRIMARY KEY,
  stock_name VARCHAR(128) NOT NULL,
  last_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  last_quantity INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'TRADING',
  current_limit_rate DECIMAL(6, 4) NOT NULL DEFAULT 0.1000,
  next_limit_rate DECIMAL(6, 4) NOT NULL DEFAULT 0.1000,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_stock_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id BIGINT NOT NULL,
  stock_code CHAR(6) NOT NULL,
  UNIQUE KEY uk_admin_stock (admin_id, stock_code),
  CONSTRAINT fk_permission_admin FOREIGN KEY (admin_id) REFERENCES admins(id),
  CONSTRAINT fk_permission_stock FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);

CREATE TABLE IF NOT EXISTS trade_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id VARCHAR(64) NOT NULL UNIQUE,
  stock_code CHAR(6) NOT NULL,
  side VARCHAR(8) NOT NULL,
  price DECIMAL(12, 2) NOT NULL,
  quantity INT NOT NULL,
  entered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  CONSTRAINT fk_order_stock FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);

CREATE TABLE IF NOT EXISTS blacklist (
  id_card_no VARCHAR(50) PRIMARY KEY,
  user_name VARCHAR(128) NOT NULL,
  fund_account_no VARCHAR(64),
  security_account_no VARCHAR(64),
  reason VARCHAR(512),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  removed_at TIMESTAMP NULL,
  INDEX idx_blacklist_user_active (user_name, active),
  INDEX idx_blacklist_card_active (id_card_no, active)
);

CREATE TABLE IF NOT EXISTS trade_reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  review_id VARCHAR(64) NOT NULL UNIQUE,
  order_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  fund_account_no VARCHAR(64) NOT NULL,
  security_account_no VARCHAR(64) NOT NULL,
  id_card_no VARCHAR(50),
  user_name VARCHAR(128),
  stock_code CHAR(6) NOT NULL,
  stock_name VARCHAR(128),
  side VARCHAR(8) NOT NULL,
  price DECIMAL(12, 2) NOT NULL,
  quantity INT NOT NULL,
  amount DECIMAL(14, 2) NOT NULL,
  approved BOOLEAN NOT NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'AUTO_APPROVED',
  risk_level VARCHAR(16) NOT NULL,
  reject_code VARCHAR(64),
  reason VARCHAR(512),
  client_time VARCHAR(64),
  manual_decision VARCHAR(16),
  manual_reason VARCHAR(512),
  decided_by VARCHAR(64),
  decided_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id BIGINT,
  username VARCHAR(64),
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64),
  target_id VARCHAR(128),
  detail VARCHAR(1024),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_created_at (created_at),
  INDEX idx_audit_admin (admin_id)
);
