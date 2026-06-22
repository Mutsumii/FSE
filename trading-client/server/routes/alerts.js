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
      `SELECT *
       FROM price_alert
       WHERE fund_account_no = ?
       ORDER BY create_time DESC, alert_id DESC`,
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
    const [result] = await pool.execute(
      `INSERT INTO price_alert (
        fund_account_no,
        stock_code,
        alert_direction,
        alert_price,
        alert_status,
        create_time,
        trigger_time
      ) VALUES (?, ?, ?, ?, ?, NOW(), NULL)`,
      [
        body.fundAccountNo,
        body.stockCode,
        body.alertDirection,
        Number(body.alertPrice),
        body.alertStatus || "ENABLED",
      ],
    );
    res.status(201).json({ ok: true, data: { alertId: result.insertId } });
  } catch (error) {
    next(error);
  }
});

router.patch("/:alertId", async (req, res, next) => {
  try {
    const alertId = Number(req.params.alertId);
    const fields = [];
    const values = [];

    if (Object.prototype.hasOwnProperty.call(req.body, "alertStatus")) {
      fields.push("alert_status = ?");
      values.push(req.body.alertStatus);
    }
    if (req.body.triggerNow) {
      fields.push("trigger_time = NOW()");
    }

    if (!fields.length) {
      return res.status(400).json({ ok: false, message: "No fields to update" });
    }

    values.push(alertId);
    const [result] = await pool.execute(
      `UPDATE price_alert SET ${fields.join(", ")} WHERE alert_id = ?`,
      values,
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ ok: false, message: "Alert not found" });
    }
    res.json({ ok: true, data: { alertId } });
  } catch (error) {
    next(error);
  }
});

module.exports = router;
