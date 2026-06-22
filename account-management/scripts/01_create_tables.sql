CREATE DATABASE IF NOT EXISTS account_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE account_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS holding_change_log;
DROP TABLE IF EXISTS holding;
DROP TABLE IF EXISTS fund_transaction_log;
DROP TABLE IF EXISTS fund_account;
DROP TABLE IF EXISTS security_account;
DROP TABLE IF EXISTS staff;
DROP TABLE IF EXISTS investor;

CREATE TABLE investor (
    investor_id INT NOT NULL AUTO_INCREMENT,
    type ENUM('个人', '法人') NOT NULL,
    name VARCHAR(100) NOT NULL,
    gender VARCHAR(10) NULL,
    id_type VARCHAR(20) NOT NULL,
    id_number VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NULL,
    address VARCHAR(200) NULL,
    work_unit VARCHAR(100) NULL,
    occupation VARCHAR(50) NULL,
    education VARCHAR(50) NULL,
    legal_number VARCHAR(20) NULL,
    business_license VARCHAR(20) NULL,
    executor_name VARCHAR(50) NULL,
    executor_id_number VARCHAR(50) NULL,
    executor_phone VARCHAR(20) NULL,
    executor_address VARCHAR(100) NULL,
    agent_name VARCHAR(100) NULL,
    agent_id_number VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (investor_id),
    UNIQUE KEY uk_investor_id_number (id_number)
) ENGINE=InnoDB COMMENT='投资者表';

CREATE TABLE staff (
    staff_id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    status ENUM('正常', '禁用') NOT NULL DEFAULT '正常',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (staff_id),
    UNIQUE KEY uk_staff_username (username)
) ENGINE=InnoDB COMMENT='工作人员表';

CREATE TABLE security_account (
    sec_acc_no VARCHAR(20) NOT NULL,
    investor_id INT NOT NULL,
    status ENUM('正常', '挂失冻结', '违规冻结', '无资金账户冻结', '预销户', '已销户') NOT NULL,
    open_date DATE NOT NULL,
    linked_fund_acc VARCHAR(20) NULL,
    PRIMARY KEY (sec_acc_no),
    UNIQUE KEY uk_security_linked_fund_acc (linked_fund_acc),
    KEY idx_security_investor_id (investor_id),
    KEY idx_security_status (status),
    CONSTRAINT fk_security_account_investor
        FOREIGN KEY (investor_id) REFERENCES investor (investor_id)
) ENGINE=InnoDB COMMENT='证券账户表';

CREATE TABLE fund_account (
    fund_acc_no VARCHAR(20) NOT NULL,
    sec_acc_no VARCHAR(20) NULL,
    trade_password VARCHAR(128) NOT NULL,
    withdraw_password VARCHAR(128) NOT NULL,
    available_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    frozen_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status ENUM('正常', '挂失冻结', '违规冻结', '已销户') NOT NULL,
    open_date DATE NOT NULL,
    last_interest_date DATE NULL,
    annual_interest_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0035,
    PRIMARY KEY (fund_acc_no),
    UNIQUE KEY uk_fund_sec_acc_no (sec_acc_no),
    KEY idx_fund_status (status),
    CONSTRAINT fk_fund_account_security
        FOREIGN KEY (sec_acc_no) REFERENCES security_account (sec_acc_no)
) ENGINE=InnoDB COMMENT='资金账户表';

CREATE TABLE fund_transaction_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    fund_acc_no VARCHAR(20) NOT NULL,
    txn_type ENUM('存款', '取款', '买入冻结', '买入扣款', '卖出回款', '撤单解冻', '结息') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    available_after DECIMAL(15,2) NOT NULL,
    frozen_after DECIMAL(15,2) NOT NULL,
    ref_order_id VARCHAR(50) NULL,
    operator_id INT NULL,
    txn_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_ftl_fund_acc_no (fund_acc_no),
    KEY idx_ftl_txn_time (txn_time),
    KEY idx_ftl_txn_type (txn_type),
    KEY idx_ftl_ref_order_id (ref_order_id),
    CONSTRAINT fk_ftl_fund_account
        FOREIGN KEY (fund_acc_no) REFERENCES fund_account (fund_acc_no),
    CONSTRAINT fk_ftl_staff
        FOREIGN KEY (operator_id) REFERENCES staff (staff_id)
) ENGINE=InnoDB COMMENT='资金流水表';

CREATE TABLE holding (
    holding_id BIGINT NOT NULL AUTO_INCREMENT,
    sec_acc_no VARCHAR(20) NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    frozen_quantity INT NOT NULL DEFAULT 0,
    avg_cost DECIMAL(15,4) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (holding_id),
    UNIQUE KEY uk_holding_sec_stock (sec_acc_no, stock_code),
    KEY idx_holding_stock_code (stock_code),
    CONSTRAINT fk_holding_security
        FOREIGN KEY (sec_acc_no) REFERENCES security_account (sec_acc_no)
) ENGINE=InnoDB COMMENT='持仓表';

CREATE TABLE holding_change_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    sec_acc_no VARCHAR(20) NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    ref_order_id VARCHAR(50) NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(15,4) NULL,
    quantity_after INT NOT NULL,
    frozen_quantity_after INT NOT NULL,
    avg_cost_after DECIMAL(15,4) NULL,
    txn_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_hcl_sec_acc_no (sec_acc_no),
    KEY idx_hcl_ref_order_id (ref_order_id),
    KEY idx_hcl_stock_code (stock_code),
    CONSTRAINT fk_hcl_security
        FOREIGN KEY (sec_acc_no) REFERENCES security_account (sec_acc_no)
) ENGINE=InnoDB COMMENT='持仓变动日志表';

CREATE TABLE operation_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    staff_id INT NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NULL,
    target_id VARCHAR(50) NULL,
    detail VARCHAR(500) NULL,
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_oplog_staff_id (staff_id),
    KEY idx_oplog_operation_time (operation_time),
    KEY idx_oplog_operation_type (operation_type),
    KEY idx_oplog_target (target_type, target_id),
    CONSTRAINT fk_operation_log_staff
        FOREIGN KEY (staff_id) REFERENCES staff (staff_id)
) ENGINE=InnoDB COMMENT='操作日志表';

ALTER TABLE security_account
    ADD CONSTRAINT fk_security_account_linked_fund
    FOREIGN KEY (linked_fund_acc) REFERENCES fund_account (fund_acc_no);

CREATE TABLE login_certificate_state (
    state_id BIGINT NOT NULL AUTO_INCREMENT,
    subject_type VARCHAR(50) NOT NULL COMMENT '主体类型：STAFF 或其他',
    subject_key VARCHAR(100) NOT NULL COMMENT '主体标识：用户名',
    certificate_verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已验证证书',
    verified_at DATETIME NULL COMMENT '验证时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (state_id),
    UNIQUE KEY uk_login_cert_subject (subject_type, subject_key)
) ENGINE=InnoDB COMMENT='登录证书验证状态表';

SET FOREIGN_KEY_CHECKS = 1;
