const express = require("express");
const pool = require("../db");

const router = express.Router();

router.get("/", async (req, res, next) => {
  try {
    const fundAccountNo = String(req.query.fundAccountNo || "");
    if (!/^[A-Za-z0-9]{6,20}$/.test(fundAccountNo)) {
      return res.status(400).json({ ok: false, message: "Invalid fundAccountNo" });
    }

    const [rows] = await pool.execute(
      `SELECT tr.*
       FROM trade_record tr
       INNER JOIN order_record ord ON ord.local_order_id = tr.local_order_id
       WHERE ord.fund_account_no = ?
       ORDER BY tr.trade_time DESC, tr.trade_id DESC`,
      [fundAccountNo],
    );
    res.json({ ok: true, data: rows });
  } catch (error) {
    next(error);
  }
});

router.post("/", async (req, res, next) => {
  try {
    const body = req.body;
    const tradePrice = Number(body.tradePrice);
    const tradeQuantity = Number(body.tradeQuantity);

    const [result] = await pool.execute(
      `INSERT INTO trade_record (
        trade_no,
        local_order_id,
        order_no,
        stock_code,
        trade_price,
        trade_quantity,
        trade_amount,
        trade_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())`,
      [
        body.tradeNo,
        Number(body.localOrderId),
        body.orderNo,
        body.stockCode,
        tradePrice,
        tradeQuantity,
        Number(body.tradeAmount ?? tradePrice * tradeQuantity),
      ],
    );

    res.status(201).json({ ok: true, data: { tradeId: result.insertId } });
  } catch (error) {
    if (error.code === "ER_DUP_ENTRY") {
      return res.json({ ok: true, duplicate: true, message: "Duplicate trade ignored" });
    }
    next(error);
  }
});

module.exports = router;
