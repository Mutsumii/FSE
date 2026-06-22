USE account_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS holding_change_log;
DROP TABLE IF EXISTS holding;
DROP TABLE IF EXISTS fund_transaction_log;
DROP TABLE IF EXISTS fund_account;
DROP TABLE IF EXISTS login_certificate_state;
DROP TABLE IF EXISTS security_account;
DROP TABLE IF EXISTS staff;
DROP TABLE IF EXISTS investor;

CREATE TABLE investor (
    investor_id INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    gender VARCHAR(10) NULL,
    id_type VARCHAR(20) NOT NULL,
    id_number VARCHAR(50) NOT NULL UNIQUE,
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE staff (
    staff_id INT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE security_account (
    sec_acc_no VARCHAR(20) PRIMARY KEY,
    investor_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    open_date DATE NOT NULL,
    linked_fund_acc VARCHAR(20) NULL UNIQUE,
    CONSTRAINT fk_security_investor
        FOREIGN KEY (investor_id) REFERENCES investor(investor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fund_account (
    fund_acc_no VARCHAR(20) PRIMARY KEY,
    sec_acc_no VARCHAR(20) NULL UNIQUE,
    trade_password VARCHAR(128) NOT NULL,
    withdraw_password VARCHAR(128) NOT NULL,
    available_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    frozen_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(20) NOT NULL,
    open_date DATE NOT NULL,
    last_interest_date DATE NULL,
    annual_interest_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0035,
    CONSTRAINT fk_fund_security
        FOREIGN KEY (sec_acc_no) REFERENCES security_account(sec_acc_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE login_certificate_state (
    state_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_type VARCHAR(20) NOT NULL,
    subject_key VARCHAR(50) NOT NULL,
    certificate_verified TINYINT(1) NOT NULL DEFAULT 0,
    verified_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_login_certificate_subject (subject_type, subject_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE security_account
    ADD CONSTRAINT fk_security_linked_fund
    FOREIGN KEY (linked_fund_acc) REFERENCES fund_account(fund_acc_no);

CREATE TABLE fund_transaction_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_acc_no VARCHAR(20) NOT NULL,
    txn_type VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    available_after DECIMAL(15,2) NOT NULL,
    frozen_after DECIMAL(15,2) NOT NULL,
    ref_order_id VARCHAR(50) NULL,
    operator_id INT NULL,
    txn_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ftl_fund_acc_no (fund_acc_no),
    KEY idx_ftl_ref_order_id (ref_order_id),
    CONSTRAINT fk_ftl_fund
        FOREIGN KEY (fund_acc_no) REFERENCES fund_account(fund_acc_no),
    CONSTRAINT fk_ftl_staff
        FOREIGN KEY (operator_id) REFERENCES staff(staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE holding (
    holding_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sec_acc_no VARCHAR(20) NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    frozen_quantity INT NOT NULL DEFAULT 0,
    avg_cost DECIMAL(15,4) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_holding_sec_stock (sec_acc_no, stock_code),
    CONSTRAINT fk_holding_security
        FOREIGN KEY (sec_acc_no) REFERENCES security_account(sec_acc_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE holding_change_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    KEY idx_hcl_ref_order_id (ref_order_id),
    CONSTRAINT fk_hcl_security
        FOREIGN KEY (sec_acc_no) REFERENCES security_account(sec_acc_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE operation_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NULL,
    target_id VARCHAR(50) NULL,
    detail VARCHAR(500) NULL,
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_operation_staff
        FOREIGN KEY (staff_id) REFERENCES staff(staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
