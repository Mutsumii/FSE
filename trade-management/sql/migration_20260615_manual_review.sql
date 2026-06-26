USE stock_trade_management;

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_column_if_missing;

DELIMITER //

CREATE PROCEDURE add_column_if_missing(
  IN tableName VARCHAR(64),
  IN columnName VARCHAR(64),
  IN columnDefinition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = tableName
      AND column_name = columnName
  ) THEN
    SET @sql = CONCAT('ALTER TABLE ', tableName, ' ADD COLUMN ', columnName, ' ', columnDefinition);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END //

DELIMITER ;

CALL add_column_if_missing('trade_reviews', 'review_status', "VARCHAR(32) NOT NULL DEFAULT 'AUTO_APPROVED' AFTER approved");
CALL add_column_if_missing('trade_reviews', 'manual_decision', "VARCHAR(16) NULL AFTER client_time");
CALL add_column_if_missing('trade_reviews', 'manual_reason', "VARCHAR(512) NULL AFTER manual_decision");
CALL add_column_if_missing('trade_reviews', 'decided_by', "VARCHAR(64) NULL AFTER manual_reason");
CALL add_column_if_missing('trade_reviews', 'decided_at', "TIMESTAMP NULL AFTER decided_by");

DROP PROCEDURE IF EXISTS add_column_if_missing;
