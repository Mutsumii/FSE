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
      `SELECT n.*
       FROM alert_notification n
       INNER JOIN price_alert a ON a.alert_id = n.alert_id
       WHERE a.fund_account_no = ?
       ORDER BY n.notify_time DESC, n.notification_id DESC`,
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
      `INSERT INTO alert_notification (
        alert_id,
        notify_content,
        read_status,
        notify_time
      ) VALUES (?, ?, ?, NOW())`,
      [
        Number(body.alertId),
        body.notifyContent,
        body.readStatus || "UNREAD",
      ],
    );
    res.status(201).json({ ok: true, data: { notificationId: result.insertId } });
  } catch (error) {
    next(error);
  }
});

router.patch("/:notificationId", async (req, res, next) => {
  try {
    const notificationId = Number(req.params.notificationId);
    const readStatus = req.body.readStatus || "READ";
    const [result] = await pool.execute(
      `UPDATE alert_notification
       SET read_status = ?
       WHERE notification_id = ?`,
      [readStatus, notificationId],
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ ok: false, message: "Notification not found" });
    }
    res.json({ ok: true, data: { notificationId, readStatus } });
  } catch (error) {
    next(error);
  }
});

module.exports = router;
