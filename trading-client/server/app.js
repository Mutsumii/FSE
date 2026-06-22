require("dotenv").config();

const express = require("express");
const path = require("path");
const cors = require("cors");
const sessionsRouter = require("./routes/sessions");
const ordersRouter = require("./routes/orders");
const tradesRouter = require("./routes/trades");
const alertsRouter = require("./routes/alerts");
const notificationsRouter = require("./routes/notifications");
const centralRouter = require("./routes/central");
const { startKafka } = require("./kafka");

const app = express();
const port = Number(process.env.PORT || 8090);

app.use(cors());
app.use(express.json());

app.get("/api/client/health", (req, res) => {
  res.json({ ok: true, service: "trading-client-api" });
});

app.use("/api/client/sessions", sessionsRouter);
app.use("/api/client/orders", ordersRouter);
app.use("/api/client/trades", tradesRouter);
app.use("/api/client/alerts", alertsRouter);
app.use("/api/client/notifications", notificationsRouter);
app.use("/api/client/central", centralRouter);

// 代理：转发外部 API 调用，解决前端跨域问题
const ACCOUNT_BACKEND = process.env.ACCOUNT_BACKEND || "http://localhost:8080";
const CENTRAL_BACKEND = process.env.CENTRAL_BACKEND || "http://localhost:8082";
const MGMT_BACKEND = process.env.MGMT_BACKEND || "http://localhost:8081";

function proxyTo(backend, errorMsg) {
  return async (req, res) => {
    try {
      const target = `${backend}${req.originalUrl}`;
      const headers = { "Content-Type": "application/json" };
      const body = req.method !== "GET" && req.method !== "HEAD"
        ? JSON.stringify(req.body) : undefined;
      const resp = await fetch(target, { method: req.method, headers, body });
      const data = await resp.json().catch(() => ({}));
      res.status(resp.status).json(data);
    } catch (err) {
      res.status(502).json({ code: 5000, message: errorMsg });
    }
  };
}

app.all("/api/external/*", proxyTo(ACCOUNT_BACKEND, "账户管理系统不可达"));
app.all("/api/central-trading/*", proxyTo(CENTRAL_BACKEND, "中央交易系统不可达"));
app.all("/api/trade-management/*", proxyTo(MGMT_BACKEND, "交易管理系统不可达"));

// 静态文件服务
app.use(express.static(path.join(__dirname, "..")));

app.use((err, req, res, next) => {
  console.error(err);
  const statusCode = err.statusCode || 500;
  res.status(statusCode).json({
    ok: false,
    message: statusCode === 500 ? "Trading client server error" : err.message,
  });
});

app.listen(port, () => {
  console.log(`Trading client API listening on http://localhost:${port}`);
});

startKafka().then((result) => {
  if (result.ok) {
    console.log("Kafka pipeline connected");
  } else {
    console.log(`Kafka pipeline not started: ${result.message}`);
  }
});
