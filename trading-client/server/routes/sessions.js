const express = require("express");
const crypto = require("crypto");
const pool = require("../db");

const router = express.Router();
const SESSION_MINUTES = 30;

function isAccountNo(value) {
  return typeof value === "string" && /^[A-Za-z0-9]{6,20}$/.test(value);
}

function isSecurityAccountNo(value) {
  return typeof value === "string" && value.length > 0 && value.length <= 20;
}

router.post("/", async (req, res, next) => {
  try {
    const fundAccountNo = String(req.body.fundAccountNo || "");
    const securityAccountNo = String(req.body.securityAccountNo || "");
    const sessionId = req.body.sessionId || crypto.randomUUID();

    if (!isAccountNo(fundAccountNo)) {
      return res.status(400).json({ ok: false, message: "Invalid fundAccountNo" });
    }
    if (!isSecurityAccountNo(securityAccountNo)) {
      return res.status(400).json({ ok: false, message: "Invalid securityAccountNo" });
    }

    await pool.execute(
      `INSERT INTO login_session (
        session_id,
        fund_account_no,
        security_account_no,
        login_time,
        last_active_time,
        session_status,
        expire_time
      ) VALUES (?, ?, ?, NOW(), NOW(), 'ACTIVE', DATE_ADD(NOW(), INTERVAL ? MINUTE))`,
      [sessionId, fundAccountNo, securityAccountNo, SESSION_MINUTES],
    );

    res.status(201).json({
      ok: true,
      data: {
        sessionId,
        fundAccountNo,
        securityAccountNo,
        sessionStatus: "ACTIVE",
        expireMinutes: SESSION_MINUTES,
      },
    });
  } catch (error) {
    next(error);
  }
});

router.patch("/:sessionId", async (req, res, next) => {
  try {
    const sessionId = String(req.params.sessionId || "");
    const action = String(req.body.action || "touch").toUpperCase();
    const nextStatus = action === "LOGOUT" || action === "EXPIRE" ? action : "ACTIVE";

    const [result] = await pool.execute(
      `UPDATE login_session
       SET last_active_time = NOW(),
           session_status = ?
       WHERE session_id = ?`,
      [nextStatus, sessionId],
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ ok: false, message: "Session not found" });
    }

    res.json({ ok: true, data: { sessionId, sessionStatus: nextStatus } });
  } catch (error) {
    next(error);
  }
});

module.exports = router;
