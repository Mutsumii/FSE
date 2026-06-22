async function createClientSession(account) {
  if (!API_CONFIG.clientBaseUrl) return { ok: true, mock: true };

  const sessionId = crypto.randomUUID();
  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientSessions,
    {
      method: "POST",
      body: {
        sessionId,
        fundAccountNo: account.accountNo,
        securityAccountNo: account.securityAccountNo || account.accountNo,
      },
    },
  );

  if (!result.ok) return result;
  const payload = result.data.data || result.data;
  return { ok: true, sessionId: payload.sessionId || sessionId };
}

async function updateClientSession(sessionId, action = "touch") {
  if (!API_CONFIG.clientBaseUrl || !sessionId) return { ok: true, mock: true };

  return requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientSession,
    {
      params: { sessionId },
      method: "PATCH",
      body: { action },
    },
  );
}

function toClientOrderStatus(status) {
  const map = {
    "未成交": "UNTRADED",
    "部分成交": "PART_TRADED",
    "已成交": "ALL_TRADED",
    "已撤销": "CANCELED",
    "撤单中": "CANCELING",
    "已过期": "EXPIRED",
    "已拒绝": "REJECTED",
  };
  return map[status] || status || "SUBMITTED";
}

function fromClientOrderStatus(status) {
  const map = {
    SUBMITTED: "未成交",
    ACCEPTED: "未成交",
    UNTRADED: "未成交",
    PART_TRADED: "部分成交",
    PARTIAL_FILLED: "部分成交",
    ALL_TRADED: "已成交",
    TRADED: "已成交",
    FILLED: "已成交",
    CANCELED: "已撤销",
    CANCELLED: "已撤销",
    CANCELING: "撤单中",
    CANCEL_REQUESTED: "撤单中",
    EXPIRED: "已过期",
    REJECTED: "已拒绝",
  };
  return map[status] || status || "未成交";
}

function toClientAlertStatus(status) {
  const map = {
    "启用": "ENABLED",
    "已触发": "TRIGGERED",
    "停用": "DISABLED",
  };
  return map[status] || status || "ENABLED";
}

function fromClientAlertStatus(status) {
  const map = {
    ENABLED: "启用",
    TRIGGERED: "已触发",
    DISABLED: "停用",
  };
  return map[status] || status || "启用";
}

function formatClientTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", { hour12: false });
}

async function fetchClientOrders(account) {
  if (!API_CONFIG.clientBaseUrl || !account?.accountNo) return { ok: true, mock: true };

  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientOrders,
    { params: { fundAccountNo: account.accountNo } },
  );
  if (!result.ok) return result;

  const rows = result.data.data || result.data || [];
  return {
    ok: true,
    orders: rows.map((row) => {
      const stock = state.stocks[row.stock_code] || {};
      return {
        id: row.order_no || `L${row.local_order_id}`,
        localOrderId: row.local_order_id,
        stockCode: row.stock_code,
        stockName: stock.name || row.stock_code,
        side: row.order_side === "SELL" ? "sell" : "buy",
        price: Number(row.order_price),
        quantity: Number(row.order_quantity),
        tradedQuantity: Number(row.traded_quantity || 0),
        remainingQuantity: Number(row.remaining_quantity || 0),
        status: fromClientOrderStatus(row.order_status),
        submitTime: formatClientTime(row.submit_time),
        updateTime: formatClientTime(row.update_time),
      };
    }),
  };
}

async function fetchClientTrades(account) {
  if (!API_CONFIG.clientBaseUrl || !account?.accountNo) return { ok: true, mock: true };

  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientTrades,
    { params: { fundAccountNo: account.accountNo } },
  );
  if (!result.ok) return result;

  const rows = result.data.data || result.data || [];
  return {
    ok: true,
    trades: rows.map((row) => {
      const stock = state.stocks[row.stock_code] || {};
      return {
        id: row.trade_no || `T${row.trade_id}`,
        tradeId: row.trade_id,
        orderId: row.order_no,
        stockCode: row.stock_code,
        stockName: stock.name || row.stock_code,
        price: Number(row.trade_price),
        quantity: Number(row.trade_quantity),
        amount: Number(row.trade_amount),
        time: formatClientTime(row.trade_time),
      };
    }),
  };
}

async function fetchClientAlerts(account) {
  if (!API_CONFIG.clientBaseUrl || !account?.accountNo) return { ok: true, mock: true };

  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientAlerts,
    { params: { fundAccountNo: account.accountNo } },
  );
  if (!result.ok) return result;

  const rows = result.data.data || result.data || [];
  return {
    ok: true,
    alerts: rows.filter((row) => row.alert_status !== "DISABLED").map((row) => {
      const stock = state.stocks[row.stock_code] || {};
      return {
        id: `A${row.alert_id}`,
        alertId: row.alert_id,
        stockCode: row.stock_code,
        stockName: stock.name || row.stock_code,
        direction: row.alert_direction,
        price: Number(row.alert_price),
        status: fromClientAlertStatus(row.alert_status),
        createTime: formatClientTime(row.create_time),
        triggerTime: formatClientTime(row.trigger_time),
      };
    }),
  };
}

async function fetchClientNotifications(account) {
  if (!API_CONFIG.clientBaseUrl || !account?.accountNo) return { ok: true, mock: true };

  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientNotifications,
    { params: { fundAccountNo: account.accountNo } },
  );
  if (!result.ok) return result;

  const rows = result.data.data || result.data || [];
  return {
    ok: true,
    notifications: rows.map((row) => ({
      id: `N${row.notification_id}`,
      notificationId: row.notification_id,
      alertId: row.alert_id,
      content: row.notify_content,
      readStatus: row.read_status || "UNREAD",
      time: formatClientTime(row.notify_time),
    })),
  };
}

async function createClientOrder(order, account) {
  if (!API_CONFIG.clientBaseUrl) return { ok: true, mock: true };

  const isBuy = order.side === "buy";
  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientOrders,
    {
      method: "POST",
      body: {
        orderNo: order.id,
        fundAccountNo: account.accountNo,
        securityAccountNo: account.securityAccountNo || account.accountNo,
        stockCode: order.stockCode,
        orderSide: isBuy ? "BUY" : "SELL",
        orderPrice: order.price,
        orderQuantity: order.quantity,
        tradedQuantity: order.tradedQuantity || 0,
        remainingQuantity: order.remainingQuantity,
        frozenAmount: isBuy ? order.remainingQuantity * order.price : 0,
        frozenQuantity: isBuy ? 0 : order.remainingQuantity,
        orderStatus: toClientOrderStatus(order.status),
      },
    },
  );

  if (!result.ok) return result;
  const payload = result.data.data || result.data;
  return { ok: true, localOrderId: payload.localOrderId };
}

async function updateClientOrder(order, patch = {}) {
  if (!API_CONFIG.clientBaseUrl || !order.localOrderId) return { ok: true, mock: true };

  return requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientOrder,
    {
      params: { localOrderId: order.localOrderId },
      method: "PATCH",
      body: {
        tradedQuantity: order.tradedQuantity,
        remainingQuantity: order.remainingQuantity,
        orderStatus: toClientOrderStatus(order.status),
        ...patch,
      },
    },
  );
}

async function createClientTrade(trade, order) {
  if (!API_CONFIG.clientBaseUrl || !order.localOrderId) return { ok: true, mock: true };

  return requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientTrades,
    {
      method: "POST",
      body: {
        tradeNo: trade.id,
        localOrderId: order.localOrderId,
        orderNo: order.id,
        stockCode: trade.stockCode,
        tradePrice: trade.price,
        tradeQuantity: trade.quantity,
        tradeAmount: trade.amount,
      },
    },
  );
}

async function createClientAlert(alert, account) {
  if (!API_CONFIG.clientBaseUrl) return { ok: true, mock: true };

  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientAlerts,
    {
      method: "POST",
      body: {
        fundAccountNo: account.accountNo,
        stockCode: alert.stockCode,
        alertDirection: alert.direction,
        alertPrice: alert.price,
        alertStatus: toClientAlertStatus(alert.status),
      },
    },
  );

  if (!result.ok) return result;
  const payload = result.data.data || result.data;
  return { ok: true, alertId: payload.alertId };
}

async function updateClientAlert(alert, patch = {}) {
  if (!API_CONFIG.clientBaseUrl || !alert.alertId) return { ok: true, mock: true };

  return requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientAlert,
    {
      params: { alertId: alert.alertId },
      method: "PATCH",
      body: {
        alertStatus: toClientAlertStatus(alert.status),
        ...patch,
      },
    },
  );
}

async function createClientNotification(alert, content) {
  if (!API_CONFIG.clientBaseUrl || !alert.alertId) return { ok: true, mock: true };

  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientNotifications,
    {
      method: "POST",
      body: {
        alertId: alert.alertId,
        notifyContent: content,
        readStatus: "UNREAD",
      },
    },
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data;
  return { ok: true, notificationId: payload.notificationId };
}

async function markClientNotificationRead(notificationId) {
  if (!API_CONFIG.clientBaseUrl || !notificationId) return { ok: true, mock: true };

  return requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.clientNotification,
    {
      params: { notificationId },
      method: "PATCH",
      body: { readStatus: "READ" },
    },
  );
}
