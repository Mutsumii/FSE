const express = require("express");
const pool = require("../db");
const {
  consumeTimedOutStockQuery,
  getCachedStockQuotes,
  getStockQuoteMiss,
  getKafkaStatus,
  publishCancelCommand,
  publishOrderCommand,
  publishStockQuery,
} = require("../kafka");

const router = express.Router();

router.get("/kafka/status", (req, res) => {
  res.json({ ok: true, data: getKafkaStatus() });
});

router.post("/orders", async (req, res, next) => {
  try {
    const body = req.body;
    const orderNo = body.orderNo || body.orderId || `C${Date.now()}`;
    const message = await publishOrderCommand({
      orderNo,
      fundAccountNo: body.fundAccountNo || body.accountId,
      stockCode: body.stockCode,
      direction: body.direction || body.side,
      price: body.price,
      quantity: body.quantity,
      timestamp: body.timestamp,
    });

    res.status(202).json({
      success: true,
      data: {
        accepted: true,
        orderNo,
        status: "SUBMITTED",
        kafkaMessage: message,
      },
    });
  } catch (error) {
    next(error);
  }
});

router.post("/orders/:orderId/cancel", async (req, res, next) => {
  try {
    const message = await publishCancelCommand({
      orderId: req.params.orderId,
      fundAccountNo: req.body.fundAccountNo || req.body.accountId,
      timestamp: req.body.timestamp,
    });

    res.status(202).json({
      success: true,
      data: {
        canceled: true,
        status: "CANCEL_REQUESTED",
        kafkaMessage: message,
      },
    });
  } catch (error) {
    next(error);
  }
});

router.post("/stock-queries", async (req, res, next) => {
  try {
    const stockCode = String(req.body.stockCode || req.query.stockCode || "");
    if (!/^\d{6}$/.test(stockCode)) {
      return res.status(400).json({ ok: false, message: "Invalid stockCode" });
    }

    const message = await publishStockQuery(stockCode);
    res.status(202).json({
      ok: true,
      message: "Stock query sent to central trading system. A response topic is still required for synchronous quote display.",
      data: message,
    });
  } catch (error) {
    next(error);
  }
});

router.get("/stocks", async (req, res, next) => {
  try {
    const keyword = String(req.query.keyword || "");
    const stocks = getCachedStockQuotes(keyword);
    const miss = getStockQuoteMiss(keyword);
    const timedOut = consumeTimedOutStockQuery(keyword);

    if (/^\d{6}$/.test(keyword) && stocks.length === 0 && miss) {
      return res.json({
        ok: false,
        notFound: true,
        pending: false,
        message: miss.message || "股票不存在",
        data: [],
      });
    }
    if (/^\d{6}$/.test(keyword) && stocks.length === 0 && timedOut) {
      return res.json({
        ok: false,
        notFound: true,
        pending: false,
        message: timedOut.message,
        data: [],
      });
    }
    if (/^\d{6}$/.test(keyword) && stocks.length === 0) {
      await publishStockQuery(keyword);
    }

    res.json({
      ok: true,
      pending: /^\d{6}$/.test(keyword) && stocks.length === 0,
      message: stocks.length ? "Quote cache returned" : "Quote query sent. Please refresh after central trading publishes client.stock.quote.",
      data: stocks,
    });
  } catch (error) {
    next(error);
  }
});

router.get("/orders/:orderId/result", async (req, res, next) => {
  try {
    const [orders] = await pool.execute(
      `SELECT order_no, stock_code, order_side, order_price, order_quantity,
              traded_quantity, remaining_quantity, order_status, reject_reason,
              submit_time, update_time
       FROM order_record
       WHERE order_no = ?
       LIMIT 1`,
      [req.params.orderId],
    );

    if (!orders.length) {
      return res.status(202).json({
        ok: false,
        pending: true,
        message: "Order has been sent. Waiting for local order record and Kafka reports.",
      });
    }

    const order = orders[0];
    const [trades] = await pool.execute(
      `SELECT trade_no, stock_code, trade_price, trade_quantity, trade_amount, trade_time
       FROM trade_record
       WHERE order_no = ?
       ORDER BY trade_time, trade_id`,
      [req.params.orderId],
    );

    res.json({
      ok: true,
      data: {
        orderId: order.order_no,
        stockCode: order.stock_code,
        side: order.order_side,
        orderPrice: Number(order.order_price),
        orderQuantity: Number(order.order_quantity),
        filledQuantity: Number(order.traded_quantity),
        tradedQuantity: Number(order.traded_quantity),
        remainingQuantity: Number(order.remaining_quantity),
        status: order.order_status,
        reason: order.reject_reason || "",
        tradePrice: trades.length ? Number(trades[trades.length - 1].trade_price) : null,
        tradeTime: trades.length ? trades[trades.length - 1].trade_time : null,
        trades: trades.map((trade) => ({
          tradeNo: trade.trade_no,
          stockCode: trade.stock_code,
          tradePrice: Number(trade.trade_price),
          tradeQuantity: Number(trade.trade_quantity),
          tradeAmount: Number(trade.trade_amount),
          tradeTime: trade.trade_time,
        })),
        submitTime: order.submit_time,
        updateTime: order.update_time,
      },
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router;
