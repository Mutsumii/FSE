USE stock_trade_management;

ALTER TABLE admins MODIFY COLUMN password_hash VARCHAR(255) NOT NULL;

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
