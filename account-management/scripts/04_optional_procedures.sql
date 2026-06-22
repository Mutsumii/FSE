USE stock_account_system;

DROP PROCEDURE IF EXISTS sp_deposit;
DROP PROCEDURE IF EXISTS sp_withdraw;
DROP PROCEDURE IF EXISTS sp_update_fund_balance;
DROP PROCEDURE IF EXISTS sp_update_security_holding;
DROP PROCEDURE IF EXISTS sp_annual_interest;

DELIMITER $$

CREATE PROCEDURE sp_deposit(
    IN p_fund_acc_no VARCHAR(20),
    IN p_amount DECIMAL(15,2),
    IN p_operator_id INT,
    OUT p_code INT,
    OUT p_message VARCHAR(128),
    OUT p_available_balance DECIMAL(15,2),
    OUT p_log_id BIGINT
)
proc_main: BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_available DECIMAL(15,2);
    DECLARE v_frozen DECIMAL(15,2);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_code = 500;
        SET p_message = '数据库异常';
        SET p_available_balance = NULL;
        SET p_log_id = NULL;
    END;

    SET p_code = 0;
    SET p_message = '成功';
    SET p_available_balance = NULL;
    SET p_log_id = NULL;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SET p_code = 400;
        SET p_message = '存款金额必须大于0';
        LEAVE proc_main;
    END IF;

    START TRANSACTION;

    SELECT status, available_balance, frozen_balance
    INTO v_status, v_available, v_frozen
    FROM fund_account
    WHERE fund_acc_no = p_fund_acc_no
    FOR UPDATE;

    IF v_status IS NULL THEN
        ROLLBACK;
        SET p_code = 404;
        SET p_message = '资金账户不存在';
        LEAVE proc_main;
    END IF;

    IF v_status <> '正常' THEN
        ROLLBACK;
        SET p_code = 409;
        SET p_message = '资金账户状态不允许存款';
        LEAVE proc_main;
    END IF;

    SET v_available = v_available + p_amount;

    UPDATE fund_account
    SET available_balance = v_available
    WHERE fund_acc_no = p_fund_acc_no;

    INSERT INTO fund_transaction_log (
        fund_acc_no, txn_type, amount, available_after, frozen_after, ref_order_id, operator_id
    ) VALUES (
        p_fund_acc_no, '存款', p_amount, v_available, v_frozen, NULL, p_operator_id
    );

    SET p_log_id = LAST_INSERT_ID();
    SET p_available_balance = v_available;

    COMMIT;
END$$

CREATE PROCEDURE sp_withdraw(
    IN p_fund_acc_no VARCHAR(20),
    IN p_amount DECIMAL(15,2),
    IN p_withdraw_password VARCHAR(128),
    IN p_operator_id INT,
    OUT p_code INT,
    OUT p_message VARCHAR(128),
    OUT p_available_balance DECIMAL(15,2),
    OUT p_log_id BIGINT
)
proc_main: BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_password VARCHAR(128);
    DECLARE v_available DECIMAL(15,2);
    DECLARE v_frozen DECIMAL(15,2);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_code = 500;
        SET p_message = '数据库异常';
        SET p_available_balance = NULL;
        SET p_log_id = NULL;
    END;

    SET p_code = 0;
    SET p_message = '成功';
    SET p_available_balance = NULL;
    SET p_log_id = NULL;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SET p_code = 400;
        SET p_message = '取款金额必须大于0';
        LEAVE proc_main;
    END IF;

    START TRANSACTION;

    SELECT status, withdraw_password, available_balance, frozen_balance
    INTO v_status, v_password, v_available, v_frozen
    FROM fund_account
    WHERE fund_acc_no = p_fund_acc_no
    FOR UPDATE;

    IF v_status IS NULL THEN
        ROLLBACK;
        SET p_code = 404;
        SET p_message = '资金账户不存在';
        LEAVE proc_main;
    END IF;

    IF v_status <> '正常' THEN
        ROLLBACK;
        SET p_code = 409;
        SET p_message = '资金账户状态不允许取款';
        LEAVE proc_main;
    END IF;

    IF v_password <> p_withdraw_password THEN
        ROLLBACK;
        SET p_code = 401;
        SET p_message = '取款密码错误';
        LEAVE proc_main;
    END IF;

    IF v_available < p_amount THEN
        ROLLBACK;
        SET p_code = 402;
        SET p_message = '可用余额不足';
        LEAVE proc_main;
    END IF;

    SET v_available = v_available - p_amount;

    UPDATE fund_account
    SET available_balance = v_available
    WHERE fund_acc_no = p_fund_acc_no;

    INSERT INTO fund_transaction_log (
        fund_acc_no, txn_type, amount, available_after, frozen_after, ref_order_id, operator_id
    ) VALUES (
        p_fund_acc_no, '取款', p_amount, v_available, v_frozen, NULL, p_operator_id
    );

    SET p_log_id = LAST_INSERT_ID();
    SET p_available_balance = v_available;

    COMMIT;
END$$

CREATE PROCEDURE sp_update_fund_balance(
    IN p_fund_acc_no VARCHAR(20),
    IN p_ref_order_id VARCHAR(50),
    IN p_txn_type VARCHAR(20),
    IN p_amount DECIMAL(15,2),
    OUT p_code INT,
    OUT p_message VARCHAR(128),
    OUT p_available_balance DECIMAL(15,2),
    OUT p_frozen_balance DECIMAL(15,2),
    OUT p_log_id BIGINT
)
proc_main: BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_available DECIMAL(15,2);
    DECLARE v_frozen DECIMAL(15,2);
    DECLARE v_exists BIGINT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_code = 500;
        SET p_message = '数据库异常';
        SET p_available_balance = NULL;
        SET p_frozen_balance = NULL;
        SET p_log_id = NULL;
    END;

    SET p_code = 0;
    SET p_message = '成功';
    SET p_available_balance = NULL;
    SET p_frozen_balance = NULL;
    SET p_log_id = NULL;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SET p_code = 400;
        SET p_message = '金额必须大于0';
        LEAVE proc_main;
    END IF;

    IF p_txn_type NOT IN ('买入冻结', '买入扣款', '卖出回款', '撤单解冻') THEN
        SET p_code = 400;
        SET p_message = '交易类型非法';
        LEAVE proc_main;
    END IF;

    START TRANSACTION;

    IF p_ref_order_id IS NOT NULL THEN
        SELECT log_id INTO v_exists
        FROM fund_transaction_log
        WHERE ref_order_id = p_ref_order_id
          AND txn_type = p_txn_type
        LIMIT 1;

        IF v_exists IS NOT NULL AND v_exists > 0 THEN
            ROLLBACK;
            SET p_code = 208;
            SET p_message = '重复请求';
            SET p_log_id = v_exists;
            LEAVE proc_main;
        END IF;
    END IF;

    SELECT status, available_balance, frozen_balance
    INTO v_status, v_available, v_frozen
    FROM fund_account
    WHERE fund_acc_no = p_fund_acc_no
    FOR UPDATE;

    IF v_status IS NULL THEN
        ROLLBACK;
        SET p_code = 404;
        SET p_message = '资金账户不存在';
        LEAVE proc_main;
    END IF;

    IF v_status <> '正常' THEN
        ROLLBACK;
        SET p_code = 409;
        SET p_message = '资金账户状态不允许交易';
        LEAVE proc_main;
    END IF;

    CASE p_txn_type
        WHEN '买入冻结' THEN
            IF v_available < p_amount THEN
                ROLLBACK;
                SET p_code = 402;
                SET p_message = '可用余额不足';
                LEAVE proc_main;
            END IF;
            SET v_available = v_available - p_amount;
            SET v_frozen = v_frozen + p_amount;
        WHEN '买入扣款' THEN
            IF v_frozen < p_amount THEN
                ROLLBACK;
                SET p_code = 403;
                SET p_message = '冻结余额不足';
                LEAVE proc_main;
            END IF;
            SET v_frozen = v_frozen - p_amount;
        WHEN '卖出回款' THEN
            SET v_available = v_available + p_amount;
        WHEN '撤单解冻' THEN
            IF v_frozen < p_amount THEN
                ROLLBACK;
                SET p_code = 403;
                SET p_message = '冻结余额不足';
                LEAVE proc_main;
            END IF;
            SET v_frozen = v_frozen - p_amount;
            SET v_available = v_available + p_amount;
    END CASE;

    UPDATE fund_account
    SET available_balance = v_available,
        frozen_balance = v_frozen
    WHERE fund_acc_no = p_fund_acc_no;

    INSERT INTO fund_transaction_log (
        fund_acc_no, txn_type, amount, available_after, frozen_after, ref_order_id, operator_id
    ) VALUES (
        p_fund_acc_no, p_txn_type, p_amount, v_available, v_frozen, p_ref_order_id, NULL
    );

    SET p_log_id = LAST_INSERT_ID();
    SET p_available_balance = v_available;
    SET p_frozen_balance = v_frozen;

    COMMIT;
END$$

CREATE PROCEDURE sp_update_security_holding(
    IN p_sec_acc_no VARCHAR(20),
    IN p_stock_code VARCHAR(10),
    IN p_change_type VARCHAR(20),
    IN p_quantity INT,
    IN p_price DECIMAL(15,4),
    OUT p_code INT,
    OUT p_message VARCHAR(128),
    OUT p_current_quantity INT,
    OUT p_current_frozen_quantity INT,
    OUT p_current_avg_cost DECIMAL(15,4)
)
proc_main: BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_holding_id BIGINT;
    DECLARE v_quantity INT DEFAULT 0;
    DECLARE v_frozen_quantity INT DEFAULT 0;
    DECLARE v_avg_cost DECIMAL(15,4);
    DECLARE v_new_avg_cost DECIMAL(15,4);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_code = 500;
        SET p_message = '数据库异常';
        SET p_current_quantity = NULL;
        SET p_current_frozen_quantity = NULL;
        SET p_current_avg_cost = NULL;
    END;

    SET p_code = 0;
    SET p_message = '成功';
    SET p_current_quantity = NULL;
    SET p_current_frozen_quantity = NULL;
    SET p_current_avg_cost = NULL;

    IF p_quantity IS NULL OR p_quantity <= 0 THEN
        SET p_code = 400;
        SET p_message = '数量必须大于0';
        LEAVE proc_main;
    END IF;

    IF p_change_type NOT IN ('买入增加', '卖出冻结', '卖出扣减', '撤单释放') THEN
        SET p_code = 400;
        SET p_message = '持仓变更类型非法';
        LEAVE proc_main;
    END IF;

    START TRANSACTION;

    SELECT status
    INTO v_status
    FROM security_account
    WHERE sec_acc_no = p_sec_acc_no
    FOR UPDATE;

    IF v_status IS NULL THEN
        ROLLBACK;
        SET p_code = 404;
        SET p_message = '证券账户不存在';
        LEAVE proc_main;
    END IF;

    IF v_status <> '正常' THEN
        ROLLBACK;
        SET p_code = 409;
        SET p_message = '证券账户状态不允许交易';
        LEAVE proc_main;
    END IF;

    SELECT holding_id, quantity, frozen_quantity, avg_cost
    INTO v_holding_id, v_quantity, v_frozen_quantity, v_avg_cost
    FROM holding
    WHERE sec_acc_no = p_sec_acc_no
      AND stock_code = p_stock_code
    FOR UPDATE;

    IF v_holding_id IS NULL THEN
        IF p_change_type <> '买入增加' THEN
            ROLLBACK;
            SET p_code = 404;
            SET p_message = '持仓记录不存在';
            LEAVE proc_main;
        END IF;

        INSERT INTO holding (
            sec_acc_no, stock_code, quantity, frozen_quantity, avg_cost
        ) VALUES (
            p_sec_acc_no, p_stock_code, p_quantity, 0, p_price
        );

        SET p_current_quantity = p_quantity;
        SET p_current_frozen_quantity = 0;
        SET p_current_avg_cost = p_price;
        COMMIT;
        LEAVE proc_main;
    END IF;

    CASE p_change_type
        WHEN '买入增加' THEN
            IF p_price IS NULL OR p_price <= 0 THEN
                ROLLBACK;
                SET p_code = 400;
                SET p_message = '买入增加时价格必须大于0';
                LEAVE proc_main;
            END IF;

            SET v_new_avg_cost =
                ((v_quantity * IFNULL(v_avg_cost, 0)) + (p_quantity * p_price))
                / (v_quantity + p_quantity);

            SET v_quantity = v_quantity + p_quantity;
            SET v_avg_cost = v_new_avg_cost;
        WHEN '卖出冻结' THEN
            IF (v_quantity - v_frozen_quantity) < p_quantity THEN
                ROLLBACK;
                SET p_code = 402;
                SET p_message = '可卖数量不足';
                LEAVE proc_main;
            END IF;
            SET v_frozen_quantity = v_frozen_quantity + p_quantity;
        WHEN '卖出扣减' THEN
            IF v_frozen_quantity < p_quantity THEN
                ROLLBACK;
                SET p_code = 403;
                SET p_message = '冻结股数不足';
                LEAVE proc_main;
            END IF;
            IF v_quantity < p_quantity THEN
                ROLLBACK;
                SET p_code = 402;
                SET p_message = '持有股数不足';
                LEAVE proc_main;
            END IF;
            SET v_quantity = v_quantity - p_quantity;
            SET v_frozen_quantity = v_frozen_quantity - p_quantity;
        WHEN '撤单释放' THEN
            IF v_frozen_quantity < p_quantity THEN
                ROLLBACK;
                SET p_code = 403;
                SET p_message = '冻结股数不足';
                LEAVE proc_main;
            END IF;
            SET v_frozen_quantity = v_frozen_quantity - p_quantity;
    END CASE;

    UPDATE holding
    SET quantity = v_quantity,
        frozen_quantity = v_frozen_quantity,
        avg_cost = v_avg_cost
    WHERE holding_id = v_holding_id;

    SET p_current_quantity = v_quantity;
    SET p_current_frozen_quantity = v_frozen_quantity;
    SET p_current_avg_cost = v_avg_cost;

    COMMIT;
END$$

CREATE PROCEDURE sp_annual_interest(
    IN p_year_rate DECIMAL(5,4),
    IN p_operator_id INT,
    OUT p_code INT,
    OUT p_message VARCHAR(128),
    OUT p_total_accounts INT,
    OUT p_total_interest DECIMAL(15,2)
)
proc_main: BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_fund_acc_no VARCHAR(20);
    DECLARE v_available DECIMAL(15,2);
    DECLARE v_frozen DECIMAL(15,2);
    DECLARE v_rate DECIMAL(5,4);
    DECLARE v_interest DECIMAL(15,2);
    DECLARE v_accounts INT DEFAULT 0;
    DECLARE v_total_interest DECIMAL(15,2) DEFAULT 0.00;

    DECLARE cur CURSOR FOR
        SELECT fund_acc_no, available_balance, frozen_balance,
               CASE WHEN p_year_rate IS NULL OR p_year_rate <= 0 THEN annual_interest_rate ELSE p_year_rate END
        FROM fund_account
        WHERE status = '正常';

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_code = 500;
        SET p_message = '数据库异常';
        SET p_total_accounts = NULL;
        SET p_total_interest = NULL;
    END;

    SET p_code = 0;
    SET p_message = '成功';
    SET p_total_accounts = 0;
    SET p_total_interest = 0.00;

    START TRANSACTION;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_fund_acc_no, v_available, v_frozen, v_rate;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;

        SET v_interest = ROUND(v_available * v_rate, 2);

        UPDATE fund_account
        SET available_balance = available_balance + v_interest,
            last_interest_date = CURRENT_DATE
        WHERE fund_acc_no = v_fund_acc_no;

        INSERT INTO fund_transaction_log (
            fund_acc_no, txn_type, amount, available_after, frozen_after, ref_order_id, operator_id
        ) VALUES (
            v_fund_acc_no, '结息', v_interest, v_available + v_interest, v_frozen, NULL, p_operator_id
        );

        SET v_accounts = v_accounts + 1;
        SET v_total_interest = v_total_interest + v_interest;
    END LOOP;

    CLOSE cur;

    COMMIT;

    SET p_total_accounts = v_accounts;
    SET p_total_interest = v_total_interest;
END$$

DELIMITER ;
