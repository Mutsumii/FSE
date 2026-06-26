USE stock_trade_management;

ALTER TABLE blacklist MODIFY COLUMN id_card_no VARCHAR(50) NOT NULL;
ALTER TABLE trade_reviews MODIFY COLUMN id_card_no VARCHAR(50) NULL;
ALTER TABLE trade_reviews ADD COLUMN stock_name VARCHAR(128) NULL AFTER stock_code;
