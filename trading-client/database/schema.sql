CREATE DATABASE IF NOT EXISTS trading_client
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE trading_client;

CREATE TABLE IF NOT EXISTS login_session (
  session_id VARCHAR(64) PRIMARY KEY,
  fund_account_no VARCHAR(16) NOT NULL,
  security_account_no VARCHAR(20) NOT NULL,
  login_time DATETIME NOT NULL,
  last_active_time DATETIME NOT NULL,
  session_status VARCHAR(20) NOT NULL,
  expire_time DATETIME NOT NULL,
  INDEX idx_login_session_account_status (fund_account_no, session_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_record (
  local_order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) NULL,
  fund_account_no VARCHAR(16) NOT NULL,
  security_account_no VARCHAR(20) NOT NULL,
  stock_code CHAR(6) NOT NULL,
  order_side VARCHAR(10) NOT NULL,
  order_price DECIMAL(18,2) NOT NULL,
  order_quantity INT NOT NULL,
  traded_quantity INT NOT NULL DEFAULT 0,
  remaining_quantity INT NOT NULL,
  frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  frozen_quantity INT NOT NULL DEFAULT 0,
  order_status VARCHAR(20) NOT NULL,
  reject_reason VARCHAR(256) NULL,
  submit_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_order_record_order_no (order_no),
  INDEX idx_order_record_account_time (fund_account_no, submit_time),
  INDEX idx_order_record_status (order_status),
  CONSTRAINT chk_order_record_side CHECK (order_side IN ('BUY', 'SELL')),
  CONSTRAINT chk_order_record_price CHECK (order_price > 0),
  CONSTRAINT chk_order_record_quantity CHECK (order_quantity > 0),
  CONSTRAINT chk_order_record_traded_quantity CHECK (traded_quantity >= 0),
  CONSTRAINT chk_order_record_remaining_quantity CHECK (remaining_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS trade_record (
  trade_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trade_no VARCHAR(32) NOT NULL,
  local_order_id BIGINT NOT NULL,
  order_no VARCHAR(32) NOT NULL,
  stock_code CHAR(6) NOT NULL,
  trade_price DECIMAL(18,2) NOT NULL,
  trade_quantity INT NOT NULL,
  trade_amount DECIMAL(18,2) NOT NULL,
  trade_time DATETIME NOT NULL,
  UNIQUE KEY uk_trade_record_trade_no (trade_no),
  INDEX idx_trade_record_local_order (local_order_id),
  CONSTRAINT fk_trade_record_order
    FOREIGN KEY (local_order_id)
    REFERENCES order_record(local_order_id),
  CONSTRAINT chk_trade_record_price CHECK (trade_price > 0),
  CONSTRAINT chk_trade_record_quantity CHECK (trade_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS price_alert (
  alert_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_account_no VARCHAR(16) NOT NULL,
  stock_code CHAR(6) NOT NULL,
  alert_direction VARCHAR(10) NOT NULL,
  alert_price DECIMAL(18,2) NOT NULL,
  alert_status VARCHAR(20) NOT NULL,
  create_time DATETIME NOT NULL,
  trigger_time DATETIME NULL,
  INDEX idx_price_alert_account_status (fund_account_no, alert_status),
  INDEX idx_price_alert_stock_status (stock_code, alert_status),
  CONSTRAINT chk_price_alert_direction CHECK (alert_direction IN ('ABOVE', 'BELOW')),
  CONSTRAINT chk_price_alert_price CHECK (alert_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alert_notification (
  notification_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alert_id BIGINT NOT NULL,
  notify_content VARCHAR(256) NOT NULL,
  read_status VARCHAR(10) NOT NULL,
  notify_time DATETIME NOT NULL,
  CONSTRAINT fk_alert_notification_alert
    FOREIGN KEY (alert_id)
    REFERENCES price_alert(alert_id),
  CONSTRAINT chk_alert_notification_read_status CHECK (read_status IN ('UNREAD', 'READ'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
