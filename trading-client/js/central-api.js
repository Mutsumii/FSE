async function fetchQuotes(keyword = "") {
  if (!API_CONFIG.centralBaseUrl && API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled) {
    const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : "";
    const result = await requestJson(
      API_CONFIG.clientBaseUrl,
      `${API_CONFIG.endpoints.kafkaQuotes}${query}`,
    );
    if (!result.ok) return result;
    if (result.data.notFound || result.data.ok === false) {
      return {
        ok: false,
        notFound: true,
        message: result.data.message || "股票不存在",
      };
    }
    const payload = result.data.data || result.data.stocks || result.data;
    const stocks = (Array.isArray(payload) ? payload : []).map(
      normalizeStockQuote,
    );
    if (!stocks.length && result.data.pending) {
      return {
        ok: false,
        pending: true,
        message: "行情请求已发送，等待中央交易系统返回后再刷新",
      };
    }
    return { ok: true, stocks };
  }

  if (!API_CONFIG.centralBaseUrl) {
    const result = searchStocks(keyword || "");
    if (result.error) return { ok: false, message: result.error };
    return { ok: true, stocks: result.stocks };
  }

  const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : "";
  const result = await requestJson(
    API_CONFIG.centralBaseUrl,
    `${API_CONFIG.endpoints.quotes}${query}`,
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data.stocks || result.data;
  const stocks = (Array.isArray(payload) ? payload : []).map(
    normalizeStockQuote,
  );
  return { ok: true, stocks };
}

function normalizeStockQuote(item) {
  return {
    stockCode: item.stockCode,
    name: item.name || item.stockName,
    latest: Number(item.latest ?? item.latestPrice ?? item.currentPrice ?? 0),
    prevClose: Number(
      item.prevClose ??
        item.previousClose ??
        item.latest ??
        item.currentPrice ??
        0,
    ),
    high: Number(
      item.high ?? item.highestPrice ?? item.latest ?? item.currentPrice ?? 0,
    ),
    low: Number(
      item.low ?? item.lowestPrice ?? item.latest ?? item.currentPrice ?? 0,
    ),
    buyOne: Number(
      item.buyOne ?? item.bidPrice ?? item.latest ?? item.currentPrice ?? 0,
    ),
    sellOne: Number(
      item.sellOne ?? item.askPrice ?? item.latest ?? item.currentPrice ?? 0,
    ),
    highLimit: Number(
      item.highLimit ?? item.limitUp ?? item.upperLimit ?? NaN,
    ),
    lowLimit: Number(
      item.lowLimit ?? item.limitDown ?? item.lowerLimit ?? NaN,
    ),
    status: item.status || item.tradeStatus || "可交易",
    announcement: item.announcement || item.notice || "",
  };
}

function normalizeOrderStatus(status) {
  const statusMap = {
    SUBMITTED: "未成交",
    ACCEPTED: "未成交",
    UNTRADED: "未成交",
    PART_TRADED: "部分成交",
    PARTIAL_FILLED: "部分成交",
    TRADED: "已成交",
    FILLED: "已成交",
    CANCELED: "已撤销",
    CANCELLED: "已撤销",
    CANCELING: "撤单中",
    CANCEL_REQUESTED: "撤单中",
    EXPIRED: "已过期",
    REJECTED: "已拒绝",
  };
  return statusMap[status] || status || "未成交";
}

async function submitOrderToCentral(orderPayload) {
  if (!API_CONFIG.centralBaseUrl)
    return submitOrderViaKafkaOrMock(orderPayload);
  const result = await requestJson(
    API_CONFIG.centralBaseUrl,
    API_CONFIG.endpoints.submitOrder,
    {
      method: "POST",
      body: orderPayload,
    },
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data.order || result.data;
  if (result.data.success === false || payload.accepted === false) {
    return {
      ok: false,
      message: result.data.message || payload.message || "中央交易系统拒绝委托",
    };
  }
  return {
    ok: true,
    orderNo: payload.orderNo || payload.orderId || `O${Date.now()}`,
    status: normalizeOrderStatus(payload.status),
  };
}

async function cancelOrderInCentral(orderId) {
  if (!API_CONFIG.centralBaseUrl) return cancelOrderViaKafkaOrMock(orderId);
  const result = await requestJson(
    API_CONFIG.centralBaseUrl,
    API_CONFIG.endpoints.cancelOrder,
    {
      params: { orderId },
      method: "POST",
    },
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data;
  if (result.data.success === false || payload.canceled === false) {
    return {
      ok: false,
      message: result.data.message || payload.message || "中央交易系统拒绝撤销",
    };
  }
  return { ok: true, status: normalizeOrderStatus(payload.status) };
}

async function fetchOrderResultFromCentral(orderId) {
  if (!API_CONFIG.centralBaseUrl && API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled) {
    const result = await requestJson(
      API_CONFIG.clientBaseUrl,
      API_CONFIG.endpoints.kafkaOrderResult,
      { params: { orderId } },
    );
    if (!result.ok) return result;
    const payload = result.data.data || result.data;
    if (result.data.pending || result.data.ok === false || payload.pending) {
      return { ok: false, pending: true, message: result.data.message || payload.message || "中央交易系统暂无成交回报" };
    }
    return { ok: true, result: payload };
  }
  if (!API_CONFIG.centralBaseUrl) return { ok: false, mock: true };
  const result = await requestJson(
    API_CONFIG.centralBaseUrl,
    API_CONFIG.endpoints.orderResult,
    { params: { orderId } },
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data;
  if (result.data.pending || result.data.ok === false || payload.pending) {
    return { ok: false, pending: true, message: result.data.message || payload.message || "中央交易系统暂无成交回报" };
  }
  return { ok: true, result: payload };
}

async function submitOrderViaKafkaOrMock(orderPayload) {
  if (!API_CONFIG.clientBaseUrl || !API_CONFIG.centralKafkaEnabled)
    return { ok: true, orderNo: `O${Date.now()}`, status: "未成交" };

  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.kafkaSubmitOrder,
    {
      method: "POST",
      body: {
        ...orderPayload,
        orderNo: orderPayload.orderNo || orderPayload.orderId || `C${Date.now()}`,
        timestamp: new Date().toISOString(),
      },
    },
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data.order || result.data;
  if (result.data.success === false || payload.accepted === false) {
    return {
      ok: false,
      message: result.data.message || payload.message || "中央交易系统 Kafka 管道拒绝委托",
    };
  }
  return {
    ok: true,
    orderNo: payload.orderNo || payload.orderId || orderPayload.orderNo || orderPayload.orderId || `C${Date.now()}`,
    status: normalizeOrderStatus(payload.status || "SUBMITTED"),
  };
}

async function cancelOrderViaKafkaOrMock(orderId) {
  if (!API_CONFIG.clientBaseUrl || !API_CONFIG.centralKafkaEnabled) return { ok: true };

  const account = currentAccount();
  const result = await requestJson(
    API_CONFIG.clientBaseUrl,
    API_CONFIG.endpoints.kafkaCancelOrder,
    {
      params: { orderId },
      method: "POST",
      body: {
        fundAccountNo: account?.accountNo,
        timestamp: new Date().toISOString(),
      },
    },
  );
  if (!result.ok) return result;
  const payload = result.data.data || result.data;
  if (result.data.success === false || payload.canceled === false) {
    return {
      ok: false,
      message: result.data.message || payload.message || "中央交易系统 Kafka 管道拒绝撤单",
    };
  }
  return { ok: true, status: normalizeOrderStatus(payload.status) };
}
