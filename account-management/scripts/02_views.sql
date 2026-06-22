USE stock_account_system;

DROP VIEW IF EXISTS v_fund_account_simple;
DROP VIEW IF EXISTS v_holding_available;
DROP VIEW IF EXISTS v_investor_basic;

CREATE VIEW v_fund_account_simple AS
SELECT
    fund_acc_no,
    sec_acc_no,
    available_balance,
    frozen_balance,
    currency,
    status,
    open_date,
    last_interest_date,
    annual_interest_rate
FROM fund_account;

CREATE VIEW v_holding_available AS
SELECT
    holding_id,
    sec_acc_no,
    stock_code,
    quantity,
    frozen_quantity,
    quantity - frozen_quantity AS available_quantity,
    avg_cost,
    updated_at
FROM holding;

CREATE VIEW v_investor_basic AS
SELECT
    investor_id,
    type,
    name,
    id_type,
    id_number,
    phone,
    address
FROM investor;
