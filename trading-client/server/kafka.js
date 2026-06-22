const fs = require("fs");
const pool = require("./db");

let Kafka;
try {
  ({ Kafka } = require("kafkajs"));
} catch (error) {
  Kafka = null;
}

const TOPICS = {
  orderCommand:
    process.env.KAFKA_TOPIC_ORDER_COMMAND || "central.order.command",
  cancelCommand:
    process.env.KAFKA_TOPIC_CANCEL_COMMAND || "central.cancel.command",
  stockQuery: process.env.KAFKA_TOPIC_STOCK_QUERY || "central.stock.query",
  stockQuote: process.env.KAFKA_TOPIC_STOCK_QUOTE || "client.stock.quote",
  tradeReport: process.env.KAFKA_TOPIC_TRADE_REPORT || "client.trade.report",
  orderReport: process.env.KAFKA_TOPIC_ORDER_REPORT || "client.order.report",
};

const STATUS_MAP = {
  ACCEPTED: "SUBMITTED",
  SUBMITTED: "SUBMITTED",
  TRADED: "TRADED",
  PART_TRADED: "PART_TRADED",
  PARTIAL_FILLED: "PART_TRADED",
  CANCELED: "CANCELED",
  CANCELLED: "CANCELED",
  EXPIRED: "EXPIRED",
  REJECTED: "REJECTED",
};

const OUTBOUND_SIDES = new Set(["BUY", "SELL"]);
const ORDER_REPORT_STATUSES = new Set(Object.keys(STATUS_MAP));
const SPRING_TYPE_HEADERS = {
  orderCommand: "com.trading.central.model.OrderCommandMsg",
  cancelCommand: "com.trading.central.model.CancelCommandMsg",
  stockQuery: "com.trading.central.model.StockQueryMsg",
};

let producer;
let consumer;
let kafkaStarted = false;
let kafkaStartError = "";
const stockQuotes = new Map();
const stockQuoteMisses = new Map();
const pendingStockQueries = new Map();
const pendingOrderReports = new Map();
const pendingTradeReports = new Map();

const kafkaRuntime = {
  producerConnected: false,
  consumerConnected: false,
  producedMessages: 0,
  receivedMessages: 0,
  invalidMessages: 0,
  ignoredMessages: 0,
  duplicateTrades: 0,
  missingOrders: 0,
  handledStockQuotes: 0,
  handledTradeReports: 0,
  handledOrderReports: 0,
  lastProducedAt: null,
  lastProducedTopic: "",
  lastReceivedAt: null,
  lastReceivedTopic: "",
  lastErrorAt: null,
  lastError: "",
  lastInvalidMessage: "",
};

function kafkaEnabled() {
  return process.env.KAFKA_ENABLED === "true";
}

function boolEnv(name, defaultValue = false) {
  const value = process.env[name];
  if (value === undefined || value === "") return defaultValue;
  return value === "true";
}

function readOptionalFile(path) {
  if (!path) return undefined;
  return fs.readFileSync(path, "utf8");
}

function buildKafkaClientConfig() {
  const config = {
    clientId: process.env.KAFKA_CLIENT_ID || "trading-client",
    brokers: (process.env.KAFKA_BROKERS || "localhost:9092")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean),
  };

  const ssl = buildKafkaSslConfig();
  if (ssl) config.ssl = ssl;

  const sasl = buildKafkaSaslConfig();
  if (sasl) config.sasl = sasl;

  return config;
}

function buildKafkaSslConfig() {
  if (!boolEnv("KAFKA_SSL")) return undefined;

  const ssl = {
    rejectUnauthorized: boolEnv("KAFKA_SSL_REJECT_UNAUTHORIZED", true),
  };

  const ca = readOptionalFile(process.env.KAFKA_SSL_CA_PATH);
  const cert = readOptionalFile(process.env.KAFKA_SSL_CERT_PATH);
  const key = readOptionalFile(process.env.KAFKA_SSL_KEY_PATH);

  if (ca) ssl.ca = [ca];
  if (cert) ssl.cert = cert;
  if (key) ssl.key = key;

  return ssl;
}

function buildKafkaSaslConfig() {
  const mechanism = process.env.KAFKA_SASL_MECHANISM;
  if (!mechanism) return undefined;

  const username = process.env.KAFKA_SASL_USERNAME;
  const password = process.env.KAFKA_SASL_PASSWORD;
  if (!username || !password) {
    throw new Error(
      "KAFKA_SASL_USERNAME and KAFKA_SASL_PASSWORD are required when KAFKA_SASL_MECHANISM is set.",
    );
  }

  return { mechanism, username, password };
}

function requireKafkaReady() {
  if (!kafkaEnabled()) {
    const error = new Error(
      "Kafka is disabled. Set KAFKA_ENABLED=true to use the central trading pipeline.",
    );
    error.statusCode = 503;
    throw error;
  }
  if (!Kafka) {
    const error = new Error(
      "kafkajs is not installed. Run npm install before enabling Kafka.",
    );
    error.statusCode = 503;
    throw error;
  }
  if (!producer || !kafkaRuntime.producerConnected) {
    const error = new Error(kafkaStartError || "Kafka is not connected yet.");
    error.statusCode = 503;
    throw error;
  }
}

async function startKafka() {
  if (!kafkaEnabled()) {
    return { ok: false, message: "Kafka disabled" };
  }
  if (!Kafka) {
    kafkaStartError = "kafkajs is not installed";
    return { ok: false, message: kafkaStartError };
  }

  try {
    const kafka = new Kafka(buildKafkaClientConfig());

    await ensureKafkaTopics(kafka);

    producer = kafka.producer();
    consumer = kafka.consumer({
      groupId: process.env.KAFKA_GROUP_ID || "trading-client-group",
    });

    await producer.connect();
    kafkaRuntime.producerConnected = true;

    await consumer.connect();
    kafkaRuntime.consumerConnected = true;

    await consumer.subscribe({
      topic: TOPICS.tradeReport,
      fromBeginning: false,
    });
    await consumer.subscribe({
      topic: TOPICS.orderReport,
      fromBeginning: false,
    });
    await consumer.subscribe({
      topic: TOPICS.stockQuote,
      fromBeginning: false,
    });
    await consumer.run({
      eachMessage: async ({ topic, message }) => {
        await handleKafkaMessage(topic, message);
      },
    });
    kafkaStarted = true;
    kafkaStartError = "";
    return { ok: true };
  } catch (error) {
    kafkaStarted = false;
    kafkaRuntime.producerConnected = false;
    kafkaRuntime.consumerConnected = false;
    kafkaStartError = error.message;
    rememberError(error);
    console.error("Kafka startup failed:", error);
    return { ok: false, message: error.message };
  }
}

async function ensureKafkaTopics(kafka) {
  const admin = kafka.admin();
  await admin.connect();
  try {
    await admin.createTopics({
      waitForLeaders: true,
      topics: Object.values(TOPICS).map((topic) => ({
        topic,
        numPartitions: 1,
        replicationFactor: 1,
      })),
    });
  } finally {
    await admin.disconnect();
  }
}

async function handleKafkaMessage(topic, message) {
  kafkaRuntime.receivedMessages += 1;
  kafkaRuntime.lastReceivedAt = new Date().toISOString();
  kafkaRuntime.lastReceivedTopic = topic;

  const parsed = parseMessage(message);
  if (!parsed.ok) {
    rememberInvalidMessage(topic, parsed.error.message);
    return;
  }

  try {
    if (topic === TOPICS.tradeReport) {
      await handleTradeReport(parsed.payload);
    } else if (topic === TOPICS.orderReport) {
      await handleOrderReport(parsed.payload);
    } else if (topic === TOPICS.stockQuote) {
      handleStockQuote(parsed.payload);
    } else {
      kafkaRuntime.ignoredMessages += 1;
    }
  } catch (error) {
    rememberError(error);
    console.error(`Kafka message handling failed on ${topic}:`, error);
  }
}

function parseMessage(message) {
  const raw = message.value ? message.value.toString("utf8") : "{}";
  try {
    return { ok: true, payload: JSON.parse(raw) };
  } catch (error) {
    return { ok: false, error };
  }
}

async function publishOrderCommand(order) {
  const value = buildOrderCommand(order);
  requireKafkaReady();
  await producer.send({
    topic: TOPICS.orderCommand,
    messages: [
      {
        key: value.orderId,
        value: JSON.stringify(value),
        headers: buildSpringJsonHeaders(SPRING_TYPE_HEADERS.orderCommand),
      },
    ],
  });
  rememberProduced(TOPICS.orderCommand);
  return value;
}

function buildOrderCommand(order) {
  const value = {
    accountId: order.fundAccountNo || order.accountId,
    securityAccountNo: order.securityAccountNo || order.secAccNo,
    orderId: order.orderNo || order.orderId,
    stockCode: String(order.stockCode || ""),
    side: order.direction || order.side,
    price: Number(order.price),
    quantity: Number(order.quantity),
    highLimit: Number(order.highLimit ?? order.limitUp ?? order.upperLimit ?? NaN),
    lowLimit: Number(order.lowLimit ?? order.limitDown ?? order.lowerLimit ?? NaN),
    timestamp: order.timestamp || new Date().toISOString(),
  };

  assertRequired(value.accountId, "accountId is required");
  assertRequired(value.orderId, "orderId is required");
  assertStockCode(value.stockCode, "stockCode must be a 6 digit code");
  assertOneOf(value.side, OUTBOUND_SIDES, "side must be BUY or SELL");
  assertPositiveNumber(value.price, "price must be greater than 0");
  assertPositiveInteger(value.quantity, "quantity must be a positive integer");
  assertIsoDate(value.timestamp, "timestamp must be an ISO 8601 string");
  if (!Number.isFinite(value.highLimit)) delete value.highLimit;
  if (!Number.isFinite(value.lowLimit)) delete value.lowLimit;
  if (!value.securityAccountNo) delete value.securityAccountNo;

  return value;
}

async function publishCancelCommand(cancel) {
  const value = buildCancelCommand(cancel);
  requireKafkaReady();
  await producer.send({
    topic: TOPICS.cancelCommand,
    messages: [
      {
        key: value.orderId,
        value: JSON.stringify(value),
        headers: buildSpringJsonHeaders(SPRING_TYPE_HEADERS.cancelCommand),
      },
    ],
  });
  rememberProduced(TOPICS.cancelCommand);
  return value;
}

function buildCancelCommand(cancel) {
  const value = {
    orderId: cancel.orderId || cancel.orderNo,
    accountId: cancel.fundAccountNo || cancel.accountId,
    timestamp: cancel.timestamp || new Date().toISOString(),
  };

  assertRequired(value.orderId, "orderId is required");
  assertRequired(value.accountId, "accountId is required");
  assertIsoDate(value.timestamp, "timestamp must be an ISO 8601 string");

  return value;
}

async function publishStockQuery(stockCode) {
  const value = buildStockQuery(stockCode);
  requireKafkaReady();
  await producer.send({
    topic: TOPICS.stockQuery,
    messages: [
      {
        key: value.stockCode,
        value: JSON.stringify(value),
        headers: buildSpringJsonHeaders(SPRING_TYPE_HEADERS.stockQuery),
      },
    ],
  });
  if (!pendingStockQueries.has(value.stockCode)) {
    pendingStockQueries.set(value.stockCode, {
      stockCode: value.stockCode,
      queryId: value.queryId,
      sentAt: Date.now(),
    });
  }
  rememberProduced(TOPICS.stockQuery);
  return value;
}

function buildStockQuery(stockCode) {
  const value = {
    stockCode: String(stockCode || ""),
    queryId: `Q${Date.now()}`,
    timestamp: new Date().toISOString(),
  };

  assertStockCode(value.stockCode, "stockCode must be a 6 digit code");
  assertIsoDate(value.timestamp, "timestamp must be an ISO 8601 string");

  return value;
}

function handleStockQuote(payload) {
  const rows = Array.isArray(payload) ? payload : payload.stocks || [payload];
  rows.forEach((item) => {
    if (isStockNotFoundQuote(item)) {
      const stockCode = String(item.stockCode);
      stockQuotes.delete(stockCode);
      stockQuoteMisses.set(stockCode, {
        stockCode,
        message: item.message || item.reason || "股票不存在",
        quoteTime: item.quoteTime || item.timestamp || new Date().toISOString(),
      });
      pendingStockQueries.delete(stockCode);
      kafkaRuntime.handledStockQuotes += 1;
      return;
    }

    const error = validateStockQuote(item);
    if (error) {
      rememberInvalidMessage(TOPICS.stockQuote, error);
      return;
    }
    const quote = normalizeStockQuote(item);
    stockQuoteMisses.delete(quote.stockCode);
    pendingStockQueries.delete(quote.stockCode);
    stockQuotes.set(quote.stockCode, quote);
    kafkaRuntime.handledStockQuotes += 1;
  });
}

function isStockNotFoundQuote(item) {
  if (!item || typeof item !== "object" || !isStockCode(item.stockCode)) {
    return false;
  }
  return (
    item.found === false ||
    item.exists === false ||
    item.errorCode === "STOCK_NOT_FOUND" ||
    item.status === "NOT_FOUND" ||
    item.tradeStatus === "不存在"
  );
}

function validateStockQuote(item) {
  if (!item || typeof item !== "object") return "stock quote must be an object";
  if (!isStockCode(item.stockCode))
    return "stock quote stockCode must be a 6 digit code";
  if (!isFiniteNumber(item.latestPrice ?? item.latest ?? item.currentPrice)) {
    return "stock quote latestPrice must be a number";
  }
  return "";
}

function normalizeStockQuote(item) {
  return {
    stockCode: String(item.stockCode),
    stockName: item.stockName || item.name || "",
    latestPrice: Number(
      item.latestPrice ?? item.latest ?? item.currentPrice ?? 0,
    ),
    previousClose: Number(
      item.previousClose ??
        item.prevClose ??
        item.latestPrice ??
        item.latest ??
        0,
    ),
    highestPrice: Number(
      item.highestPrice ?? item.high ?? item.latestPrice ?? item.latest ?? 0,
    ),
    lowestPrice: Number(
      item.lowestPrice ?? item.low ?? item.latestPrice ?? item.latest ?? 0,
    ),
    bidPrice: Number(
      item.bidPrice ?? item.buyOne ?? item.latestPrice ?? item.latest ?? 0,
    ),
    askPrice: Number(
      item.askPrice ?? item.sellOne ?? item.latestPrice ?? item.latest ?? 0,
    ),
    highLimit: Number(
      item.highLimit ?? item.limitUp ?? item.upperLimit ?? NaN,
    ),
    lowLimit: Number(
      item.lowLimit ?? item.limitDown ?? item.lowerLimit ?? NaN,
    ),
    tradeStatus: item.tradeStatus || item.status || "可交易",
    notice: item.notice || item.announcement || "",
    quoteTime: item.quoteTime || item.timestamp || new Date().toISOString(),
  };
}

function getCachedStockQuotes(keyword = "") {
  const query = String(keyword || "").trim();
  const rows = Array.from(stockQuotes.values());
  if (!query) return rows;
  return rows.filter(
    (item) =>
      item.stockCode === query ||
      (item.stockName && item.stockName.includes(query)),
  );
}

function getStockQuoteMiss(keyword = "") {
  const query = String(keyword || "").trim();
  if (!isStockCode(query)) return null;
  return stockQuoteMisses.get(query) || null;
}

function consumeTimedOutStockQuery(keyword = "") {
  const query = String(keyword || "").trim();
  if (!isStockCode(query)) return null;

  const pending = pendingStockQueries.get(query);
  if (!pending) return null;

  const timeoutMs = Number(process.env.KAFKA_STOCK_QUERY_TIMEOUT_MS || 8000);
  if (Date.now() - pending.sentAt < timeoutMs) return null;

  pendingStockQueries.delete(query);
  return {
    stockCode: query,
    message: "股票不存在或中央交易系统未返回行情",
    sentAt: new Date(pending.sentAt).toISOString(),
    timeoutMs,
  };
}

async function handleTradeReport(report) {
  const error = validateTradeReport(report);
  if (error) {
    rememberInvalidMessage(TOPICS.tradeReport, error);
    return;
  }

  const orderNos = [
    report.buyerOrderId,
    report.sellOrderId,
    report.sellerOrderId,
    report.orderId,
    report.orderNo,
  ].filter(Boolean);
  const tradeQuantity = Number(report.tradeQuantity ?? report.quantity);
  const tradePrice = Number(report.tradePrice ?? report.price);

  for (const orderNo of orderNos) {
    await applyTradeReportToOrder(orderNo, report, tradePrice, tradeQuantity);
  }
}

function validateTradeReport(report) {
  if (!report || typeof report !== "object")
    return "trade report must be an object";
  const orderNos = [
    report.buyerOrderId,
    report.sellOrderId,
    report.sellerOrderId,
    report.orderId,
    report.orderNo,
  ].filter(Boolean);
  if (!orderNos.length)
    return "trade report must include orderId, buyerOrderId, or sellerOrderId";
  if (!report.tradeNo) return "trade report tradeNo is required";
  if (!isStockCode(report.stockCode))
    return "trade report stockCode must be a 6 digit code";
  if (!isPositiveNumber(report.tradePrice ?? report.price)) {
    return "trade report tradePrice must be greater than 0";
  }
  if (!isPositiveInteger(report.tradeQuantity ?? report.quantity)) {
    return "trade report tradeQuantity must be a positive integer";
  }
  if (report.tradeTime && !isIsoDate(report.tradeTime)) {
    return "trade report tradeTime must be an ISO 8601 string";
  }
  return "";
}

async function applyTradeReportToOrder(
  orderNo,
  report,
  tradePrice,
  tradeQuantity,
) {
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [orders] = await connection.execute(
      `SELECT local_order_id, order_quantity, traded_quantity, stock_code
       FROM order_record
       WHERE order_no = ?
       LIMIT 1
       FOR UPDATE`,
      [orderNo],
    );
    if (!orders.length) {
      kafkaRuntime.missingOrders += 1;
      storePendingTradeReport(orderNo, report);
      await connection.rollback();
      return;
    }

    const order = orders[0];
    const tradeNo = `${report.tradeNo}-${order.local_order_id}`;
    const [insert] = await connection.execute(
      `INSERT IGNORE INTO trade_record (
         trade_no, local_order_id, order_no, stock_code, trade_price,
         trade_quantity, trade_amount, trade_time
       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        tradeNo,
        order.local_order_id,
        orderNo,
        report.stockCode || order.stock_code,
        tradePrice,
        tradeQuantity,
        tradePrice * tradeQuantity,
        toMysqlDateTime(report.tradeTime),
      ],
    );

    if (insert.affectedRows === 0) {
      kafkaRuntime.duplicateTrades += 1;
      await connection.rollback();
      return;
    }

    const nextTradedQuantity = Number(order.traded_quantity) + tradeQuantity;
    if (nextTradedQuantity > Number(order.order_quantity)) {
      rememberInvalidMessage(
        TOPICS.tradeReport,
        `trade quantity exceeds remaining quantity for order ${orderNo}`,
      );
      await connection.rollback();
      return;
    }

    const remainingQuantity = Number(order.order_quantity) - nextTradedQuantity;
    const orderStatus = remainingQuantity === 0 ? "TRADED" : "PART_TRADED";

    await connection.execute(
      `UPDATE order_record
       SET traded_quantity = ?, remaining_quantity = ?, order_status = ?, update_time = NOW()
       WHERE local_order_id = ?`,
      [
        nextTradedQuantity,
        remainingQuantity,
        orderStatus,
        order.local_order_id,
      ],
    );

    await connection.commit();
    kafkaRuntime.handledTradeReports += 1;
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    connection.release();
  }
}

async function handleOrderReport(report) {
  const error = validateOrderReport(report);
  if (error) {
    rememberInvalidMessage(TOPICS.orderReport, error);
    return;
  }

  const orderNo = report.orderId || report.orderNo;
  const status =
    STATUS_MAP[report.status] ||
    STATUS_MAP[report.result] ||
    report.status ||
    report.result;
  const rejectReason = report.reason || report.message || null;

  const terminalWithoutRemainder = ["CANCELED", "EXPIRED", "REJECTED"].includes(
    status,
  );
  const [result] = await pool.execute(
    terminalWithoutRemainder
      ? `UPDATE order_record
         SET order_status = ?, reject_reason = ?, remaining_quantity = 0, update_time = NOW()
         WHERE order_no = ?`
      : `UPDATE order_record
         SET order_status = ?, reject_reason = ?, update_time = NOW()
         WHERE order_no = ?`,
    [status, rejectReason, orderNo],
  );

  if (result.affectedRows === 0) {
    kafkaRuntime.missingOrders += 1;
    storePendingOrderReport(orderNo, report);
    return;
  }

  kafkaRuntime.handledOrderReports += 1;
}

function validateOrderReport(report) {
  if (!report || typeof report !== "object")
    return "order report must be an object";
  if (!report.orderId && !report.orderNo)
    return "order report orderId is required";
  const status = report.status || report.result;
  if (!status) return "order report status is required";
  if (!ORDER_REPORT_STATUSES.has(status)) {
    return `order report status is unsupported: ${status}`;
  }
  if (report.timestamp && !isIsoDate(report.timestamp)) {
    return "order report timestamp must be an ISO 8601 string";
  }
  return "";
}

function getKafkaStatus() {
  const clientConfig = buildStatusClientConfig();
  return {
    enabled: kafkaEnabled(),
    started: kafkaStarted,
    ready:
      kafkaEnabled() &&
      kafkaStarted &&
      kafkaRuntime.producerConnected &&
      kafkaRuntime.consumerConnected,
    error: kafkaStartError || kafkaRuntime.lastError,
    client: clientConfig,
    topics: TOPICS,
    runtime: { ...kafkaRuntime },
    quoteCacheSize: stockQuotes.size,
    quoteMissSize: stockQuoteMisses.size,
    pendingStockQueries: pendingStockQueries.size,
    pendingReports: {
      orderReports: pendingOrderReports.size,
      tradeReportOrders: pendingTradeReports.size,
    },
  };
}

async function applyPendingKafkaReportsForOrder(orderNo) {
  if (!orderNo) return;

  const orderReport = pendingOrderReports.get(orderNo);
  if (orderReport) {
    pendingOrderReports.delete(orderNo);
    await handleOrderReport(orderReport);
  }

  const tradeReports = pendingTradeReports.get(orderNo) || [];
  if (tradeReports.length) {
    pendingTradeReports.delete(orderNo);
    for (const report of tradeReports) {
      const tradeQuantity = Number(report.tradeQuantity ?? report.quantity);
      const tradePrice = Number(report.tradePrice ?? report.price);
      await applyTradeReportToOrder(orderNo, report, tradePrice, tradeQuantity);
    }
  }
}

function buildStatusClientConfig() {
  return {
    clientId: process.env.KAFKA_CLIENT_ID || "trading-client",
    brokers: (process.env.KAFKA_BROKERS || "localhost:9092")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean),
    groupId: process.env.KAFKA_GROUP_ID || "trading-client-group",
    ssl: boolEnv("KAFKA_SSL"),
    saslMechanism: process.env.KAFKA_SASL_MECHANISM || "",
  };
}

function rememberProduced(topic) {
  kafkaRuntime.producedMessages += 1;
  kafkaRuntime.lastProducedAt = new Date().toISOString();
  kafkaRuntime.lastProducedTopic = topic;
}

function storePendingOrderReport(orderNo, report) {
  pendingOrderReports.set(orderNo, report);
}

function storePendingTradeReport(orderNo, report) {
  const reports = pendingTradeReports.get(orderNo) || [];
  reports.push(report);
  pendingTradeReports.set(orderNo, reports.slice(-20));
}

function buildSpringJsonHeaders(typeId) {
  if (process.env.KAFKA_SPRING_TYPE_HEADERS === "false") return undefined;
  return { __TypeId__: typeId };
}

function rememberError(error) {
  kafkaRuntime.lastErrorAt = new Date().toISOString();
  kafkaRuntime.lastError = error.message || String(error);
}

function rememberInvalidMessage(topic, reason) {
  kafkaRuntime.invalidMessages += 1;
  kafkaRuntime.lastInvalidMessage = `${topic}: ${reason}`;
  console.warn(`Invalid Kafka message on ${topic}: ${reason}`);
}

function validationError(message) {
  const error = new Error(message);
  error.statusCode = 400;
  return error;
}

function assertRequired(value, message) {
  if (value === undefined || value === null || value === "") {
    throw validationError(message);
  }
}

function assertStockCode(value, message) {
  if (!isStockCode(value)) throw validationError(message);
}

function assertOneOf(value, allowed, message) {
  if (!allowed.has(value)) throw validationError(message);
}

function assertPositiveNumber(value, message) {
  if (!isPositiveNumber(value)) throw validationError(message);
}

function assertPositiveInteger(value, message) {
  if (!isPositiveInteger(value)) throw validationError(message);
}

function assertIsoDate(value, message) {
  if (!isIsoDate(value)) throw validationError(message);
}

function isStockCode(value) {
  return /^\d{6}$/.test(String(value || ""));
}

function isFiniteNumber(value) {
  const number = Number(value);
  return Number.isFinite(number);
}

function isPositiveNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0;
}

function isPositiveInteger(value) {
  const number = Number(value);
  return Number.isInteger(number) && number > 0;
}

function isIsoDate(value) {
  if (typeof value !== "string" || !value) return false;
  return !Number.isNaN(Date.parse(value));
}

function toMysqlDateTime(value) {
  if (typeof value === "string" && value.length >= 19 && isIsoDate(value)) {
    return value.slice(0, 19).replace("T", " ");
  }
  return new Date().toISOString().slice(0, 19).replace("T", " ");
}

module.exports = {
  TOPICS,
  applyPendingKafkaReportsForOrder,
  getKafkaStatus,
  startKafka,
  publishOrderCommand,
  publishCancelCommand,
  publishStockQuery,
  getCachedStockQuotes,
  getStockQuoteMiss,
  consumeTimedOutStockQuery,
};
