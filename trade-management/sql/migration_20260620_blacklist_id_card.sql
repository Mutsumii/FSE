USE stock_trade_management;

ALTER TABLE blacklist ADD COLUMN id_card_no VARCHAR(50) NULL FIRST;
UPDATE blacklist SET id_card_no = CONCAT('LEGACY-', id) WHERE id_card_no IS NULL;
ALTER TABLE blacklist
  MODIFY COLUMN id BIGINT NOT NULL,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (id_card_no);
ALTER TABLE blacklist DROP COLUMN id;

ALTER TABLE trade_reviews ADD COLUMN id_card_no VARCHAR(50) NULL AFTER security_account_no;
