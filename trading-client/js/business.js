const ORDER_OPEN_STATUSES = ["未成交", "部分成交", "撤单中"];
const CLIENT_SYNC_INTERVAL_MS = 5000;
const ALERT_CHECK_INTERVAL_MS = 15000;
const NOTIFICATION_SYNC_INTERVAL_MS = 15000;
const pendingCertificateAuth = {
  accountNo: "",
  password: "",
  subjectType: "",
  subjectKey: "",
};
const backgroundTimers = {
  orderSync: null,
  alertCheck: null,
  notificationSync: null,
};
const operationLocks = new Set();
let sharedPortalSyncTimer = null;
const SHARED_PORTAL_COOKIE = "shared_investor_session";

function validateSession() {
  if (!state.session || !state.currentAccount) return false;
  if (Date.now() - state.session.lastActiveAt > SESSION_LIMIT_MS) {
    logout("会话已超时，请重新登录");
    return false;
  }
  state.session.lastActiveAt = Date.now();
  saveState();
  return true;
}

function isClientAuthInvalid(result) {
  return result?.code === 1018 || result?.symbol === "ERR_018";
}

function handleInvalidClientAuth(result) {
  logout("账户系统登录态已失效，请重新登录");
  return { ok: false, message: result?.message || "账户系统登录态已失效，请重新登录" };
}

async function login(accountNo, password) {
  // 联调模式（配置了后端地址）接受非纯数字卡号，mock 模式要求16位数字
  const isExternal = Boolean(API_CONFIG.accountBaseUrl);
  if (isExternal ? !accountNo.trim() : !/^\d{16}$/.test(accountNo))
    return { ok: false, message: isExternal ? "请输入资金账户卡号" : "卡号格式错误，请输入16位数字" };
  if (isExternal ? !password.trim() : !/^\d{6}$/.test(password))
    return { ok: false, message: isExternal ? "请输入交易密码" : "密码格式错误，请输入6位数字" };
  const authResult = await verifyFundAccount(accountNo, password);
  if (!authResult.ok) return authResult;
  if (authResult.requiresCertificate) {
    pendingCertificateAuth.accountNo = accountNo;
    pendingCertificateAuth.password = password;
    pendingCertificateAuth.subjectType = authResult.certificateSubjectType || "FUND";
    pendingCertificateAuth.subjectKey = authResult.certificateSubjectKey || accountNo;
    return {
      ok: true,
      requiresCertificate: true,
      message: authResult.message || "首次登录需要完成安全证书认证",
    };
  }

  clearPendingCertificateAuth();
  return finalizeClientLogin(accountNo, authResult.account);
}

async function completeInvestorCertificate(certificateCode) {
  const trimmedCode = certificateCode.trim();
  if (!pendingCertificateAuth.accountNo || !pendingCertificateAuth.subjectKey) {
    return { ok: false, message: "未找到待认证的登录请求，请重新登录" };
  }
  if (!trimmedCode) {
    return { ok: false, message: "请输入安全证书认证码" };
  }

  const authResult = await completeFundCertificate(
    pendingCertificateAuth.subjectType,
    pendingCertificateAuth.subjectKey,
    trimmedCode,
  );
  if (!authResult.ok) return authResult;
  if (authResult.requiresCertificate) {
    return { ok: false, message: "证书认证未完成，请重试" };
  }

  const result = await finalizeClientLogin(pendingCertificateAuth.accountNo, authResult.account);
  if (result.ok) clearPendingCertificateAuth();
  return result;
}

async function finalizeClientLogin(accountNo, remoteAccount) {
  const authResult = { account: remoteAccount };

  const account = ensureLocalAccount(authResult.account);
  if (account.status === "锁定") return { ok: false, message: "账户已锁定，请联系客服" };
  if (account.status !== "正常") return { ok: false, message: `账户状态异常：${account.status}` };
  if (!authResult.account.securityAccountLinked) return { ok: false, message: "证券账户未关联" };

  account.failedAttempts = 0;
  const firstLogin = !account.firstLoginDone;
  account.firstLoginDone = true;
  const sessionResult = await createClientSession(account);
  if (!sessionResult.ok) return { ok: false, message: sessionResult.message || "交易客户端会话写入失败" };
  state.currentAccount = accountNo;
  state.session = {
    accountNo,
    sessionId: sessionResult.sessionId || authResult.token || crypto.randomUUID(),
    token: authResult.token || sessionResult.sessionId || crypto.randomUUID(),
    lastActiveAt: Date.now(),
  };
  writeSharedPortalSession(account);
  saveState();
  if (redirectBackToPortalIfNeeded(account)) {
    return { ok: true, redirected: true, message: "登录成功，正在跳转回网上信息发布" };
  }
  await restoreClientState({ silent: true });
  return { ok: true, message: firstLogin ? "首次登录证书认证通过" : "登录成功" };
}

function clearPendingCertificateAuth() {
  pendingCertificateAuth.accountNo = "";
  pendingCertificateAuth.password = "";
  pendingCertificateAuth.subjectType = "";
  pendingCertificateAuth.subjectKey = "";
}

function ensureLocalAccount(remoteAccount) {
  const existing = state.accounts[remoteAccount.accountNo];
  const fallback = existing || {
    accountNo: remoteAccount.accountNo,
    tradePassword: "",
    withdrawPassword: "",
    failedAttempts: 0,
    holdings: [],
  };
  state.accounts[remoteAccount.accountNo] = {
    ...fallback,
    name: remoteAccount.name,
    status: remoteAccount.status,
    securityAccountNo: remoteAccount.securityAccountNo || fallback.securityAccountNo || remoteAccount.accountNo,
    availableCash: remoteAccount.availableCash,
    frozenCash: remoteAccount.frozenCash,
    authToken: remoteAccount.authToken || fallback.authToken || "",
    firstLoginDone: Boolean(fallback.firstLoginDone || remoteAccount.firstLoginDone),
    securityAccountLinked: remoteAccount.securityAccountLinked,
  };
  return state.accounts[remoteAccount.accountNo];
}

function logout(message = "") {
  if (state.session?.sessionId) updateClientSession(state.session.sessionId, "LOGOUT");
  stopClientBackgroundJobs();
  clearSharedPortalSession();
  state.currentAccount = null;
  state.session = null;
  saveState();
  dom.loginView.classList.remove("hidden");
  dom.terminalView.classList.add("hidden");
  if (message) setMessage(dom.loginMessage, message, "error");
}

async function bootSession() {
  if (state.session && Date.now() - state.session.lastActiveAt <= SESSION_LIMIT_MS) {
    state.currentAccount = state.session.accountNo;
    const account = currentAccount();
    if (account) {
      writeSharedPortalSession(account);
      if (redirectBackToPortalIfNeeded(account)) return;
    }
    showTerminal();
    await restoreClientState({ silent: true });
    startClientBackgroundJobs();
  } else {
    logout();
  }
}

function redirectBackToPortalIfNeeded(account) {
  const currentUrl = new URL(window.location.href);
  const returnUrl = currentUrl.searchParams.get("returnUrl");
  if (!returnUrl) return false;
  try {
    const target = new URL(returnUrl, window.location.origin);
    target.searchParams.set("loggedIn", "1");
    target.searchParams.set("fundAccNo", account.accountNo || "");
    target.searchParams.set("role", "STANDARD");
    if (account.securityAccountNo) target.searchParams.set("secAccNo", account.securityAccountNo);
    if (account.name) target.searchParams.set("investorName", account.name);
    window.location.href = target.toString();
    return true;
  } catch {
    return false;
  }
}

function writeSharedPortalSession(account) {
  if (!account?.accountNo) return;
  const payload = encodeURIComponent(JSON.stringify({
    fundAccountNo: account.accountNo,
    securityAccountNo: account.securityAccountNo || "",
    role: "STANDARD",
    updatedAt: Date.now(),
  }));
  document.cookie = `${SHARED_PORTAL_COOKIE}=${payload}; path=/; max-age=86400; SameSite=Lax`;
}

function clearSharedPortalSession() {
  document.cookie = `${SHARED_PORTAL_COOKIE}=; path=/; max-age=0; SameSite=Lax`;
}

function readSharedPortalSession() {
  const cookies = document.cookie ? document.cookie.split("; ") : [];
  const entry = cookies.find((item) => item.startsWith(`${SHARED_PORTAL_COOKIE}=`));
  if (!entry) return null;
  try {
    return JSON.parse(decodeURIComponent(entry.substring(SHARED_PORTAL_COOKIE.length + 1)));
  } catch {
    return null;
  }
}

async function restoreClientState({ silent = false } = {}) {
  const account = currentAccount();
  if (!account || !API_CONFIG.clientBaseUrl) return;

  const [ordersResult, tradesResult, alertsResult, notificationsResult] =
    await Promise.all([
      fetchClientOrders(account),
      fetchClientTrades(account),
      fetchClientAlerts(account),
      fetchClientNotifications(account),
    ]);

  if (ordersResult.ok && !ordersResult.mock) state.orders = ordersResult.orders;
  if (tradesResult.ok && !tradesResult.mock) state.trades = tradesResult.trades;
  if (alertsResult.ok && !alertsResult.mock) state.alerts = alertsResult.alerts;
  if (notificationsResult.ok && !notificationsResult.mock) {
    state.notifications = notificationsResult.notifications;
  }

  const failures = [ordersResult, tradesResult, alertsResult, notificationsResult]
    .filter((result) => !result.ok);
  if (failures.length && !silent) {
    toast(failures[0].message || "交易客户端历史数据恢复失败");
  }

  saveState();
  renderAll();
}

function startClientBackgroundJobs() {
  stopClientBackgroundJobs();
  if (!state.session) return;

  backgroundTimers.orderSync = setInterval(() => {
    syncOpenOrders({ silent: true });
  }, CLIENT_SYNC_INTERVAL_MS);
  backgroundTimers.alertCheck = setInterval(() => {
    refreshAlertMonitor({ silent: true });
  }, ALERT_CHECK_INTERVAL_MS);
  backgroundTimers.notificationSync = setInterval(() => {
    refreshClientNotifications({ silent: true });
  }, NOTIFICATION_SYNC_INTERVAL_MS);
  sharedPortalSyncTimer = setInterval(() => {
    const sharedSession = readSharedPortalSession();
    if (!sharedSession?.fundAccountNo && state.session) {
      logout("门户已退出，交易客户端已同步登出");
    }
  }, 1200);

  syncOpenOrders({ silent: true });
  refreshAlertMonitor({ silent: true });
  refreshClientNotifications({ silent: true });
}

function stopClientBackgroundJobs() {
  Object.keys(backgroundTimers).forEach((key) => {
    if (backgroundTimers[key]) clearInterval(backgroundTimers[key]);
    backgroundTimers[key] = null;
  });
  if (sharedPortalSyncTimer) clearInterval(sharedPortalSyncTimer);
  sharedPortalSyncTimer = null;
}

function searchStocks(keyword) {
  const query = keyword.trim();
  if (!query) return { error: "请输入股票名称或代码" };
  if (/^\d+$/.test(query) && !/^\d{6}$/.test(query)) return { error: "股票代码格式错误，请输入6位数字" };
  const stocks = Object.values(state.stocks).filter((stock) => stock.stockCode === query || stock.name.includes(query));
  if (!stocks.length) return { error: "未找到匹配的股票，请重新输入" };
  return { stocks };
}

function validateOrderInput(form, side) {
  const stockCode = form.stockCode.value.trim();
  const rawPrice = form.price.value.trim();
  const rawQuantity = form.quantity.value.trim();
  if (!/^\d{6}$/.test(stockCode)) return { error: "股票代码格式错误，请输入6位数字" };
  const stock = state.stocks[stockCode];
  if (!stock) return { error: side === "buy" ? "该股票代码不存在" : "您未持有该股票" };
  if (stock.status !== "可交易") return { error: "该股票当前暂停交易" };
  if (!/^\d+(\.\d{1,2})?$/.test(rawPrice)) return { error: "价格必须大于0，且最多保留2位小数" };
  const orderPrice = Number(rawPrice);
  if (orderPrice <= 0) return { error: "价格必须大于0" };
  const limits = getLimits(stock);
  if (orderPrice < limits.lower || orderPrice > limits.upper) return { error: "价格不能超出涨跌停限制范围" };
  if (!/^\d+$/.test(rawQuantity)) return { error: "数量必须为整数" };
  const quantity = Number(rawQuantity);
  if (quantity <= 0 || quantity % 100 !== 0) return { error: side === "buy" ? "购买数量必须为100的整数倍" : "出售数量必须为100的整数倍" };
  return { stock, stockCode, orderPrice, quantity };
}

async function refreshExternalData({ randomizeMockQuotes = false } = {}) {
  const account = currentAccount();
  if (!account) return;

  const fundResult = await fetchFundAccount(account.accountNo);
  if (isClientAuthInvalid(fundResult)) {
    return handleInvalidClientAuth(fundResult);
  }
  if (fundResult.ok) {
    account.availableCash = fundResult.account.availableCash;
    account.frozenCash = fundResult.account.frozenCash;
    account.status = fundResult.account.status;
  } else {
    toast(fundResult.message || "资金账户信息刷新失败");
  }

  const holdingResult = await fetchSecurityHoldings(account.accountNo);
  if (isClientAuthInvalid(holdingResult)) {
    return handleInvalidClientAuth(holdingResult);
  }
  if (holdingResult.ok) account.holdings = holdingResult.holdings;

  if (API_CONFIG.centralBaseUrl || (API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled)) {
    const quoteResult = await fetchQuotes();
    if (quoteResult.ok) {
      quoteResult.stocks.forEach((stock) => {
        state.stocks[stock.stockCode] = stock;
      });
    } else {
      toast(quoteResult.message || "中央交易系统行情刷新失败");
    }
  } else if (randomizeMockQuotes) {
    Object.values(state.stocks).forEach((stock) => {
      const drift = stock.latest * (Math.random() * 0.012 - 0.006);
      stock.latest = Number(Math.max(0.01, stock.latest + drift).toFixed(2));
      stock.buyOne = Number((stock.latest - 0.01).toFixed(2));
      stock.sellOne = Number((stock.latest + 0.01).toFixed(2));
      stock.high = Math.max(stock.high, stock.latest);
      stock.low = Math.min(stock.low, stock.latest);
    });
  }

  await checkAlerts();
  saveState();
  renderAll();
}

function centralTradingManagesAssets() {
  return Boolean(
    API_CONFIG.centralBaseUrl ||
      (API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled),
  );
}

async function submitOrder(form, side) {
  if (!validateSession()) return;
  const lockKey = `submit:${side}`;
  if (operationLocks.has(lockKey)) return;
  operationLocks.add(lockKey);
  const submitButton = form.querySelector("button[type='submit']");
  const previousButtonText = submitButton?.textContent;
  if (submitButton) {
    submitButton.disabled = true;
    submitButton.textContent = "提交中";
  }
  const message = form.querySelector(".form-message");
  try {
    const account = currentAccount();
    const pendingStockCode = form.stockCode.value.trim();
    if ((API_CONFIG.centralBaseUrl || (API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled)) && /^\d{6}$/.test(pendingStockCode)) {
      const quoteResult = await fetchQuotes(pendingStockCode);
      if (quoteResult.ok) {
        quoteResult.stocks.forEach((stock) => {
          state.stocks[stock.stockCode] = stock;
        });
      }
    }
    const input = validateOrderInput(form, side);
    if (input.error) return setMessage(message, input.error, "error");

    const orderSeed = Date.now();
    const orderId = `C${orderSeed}`;
    const limits = getLimits(input.stock);
    const orderDraft = {
      reviewId: `R${orderSeed}`,
      orderId,
      orderNo: orderId,
      fundAccountNo: account.accountNo,
      securityAccountNo: account.securityAccountNo || account.accountNo,
      userName: account.name || "投资者",
      stockCode: input.stockCode,
      direction: side === "buy" ? "BUY" : "SELL",
      price: input.orderPrice,
      quantity: input.quantity,
      highLimit: limits.upper,
      lowLimit: limits.lower,
      clientTime: new Date().toISOString(),
    };

    const review = await reviewOrderByManagement(orderDraft);
    if (!review.ok || !review.approved) return setMessage(message, review.message || "交易管理系统审查未通过", "error");

    const centralOwnsAssets = centralTradingManagesAssets();
    if (side === "buy") {
      const amount = input.orderPrice * input.quantity;
      if (amount > account.availableCash) return setMessage(message, "购买金额超出可用资金", "error");
      const freezeResult = centralOwnsAssets ? { ok: true } : await freezeFunds(account.accountNo, amount, `LOCAL-${Date.now()}`);
      if (!freezeResult.ok) return setMessage(message, freezeResult.message || "资金冻结失败", "error");
      if (!centralOwnsAssets) {
        account.availableCash -= amount;
        account.frozenCash += amount;
      }
    } else {
      const holding = account.holdings.find((item) => item.stockCode === input.stockCode);
      if (!holding) return setMessage(message, "您未持有该股票", "error");
      if (input.quantity > holding.sellable) return setMessage(message, "出售数量超过可卖股数", "error");
      const freezeResult = centralOwnsAssets ? { ok: true } : await freezeHolding(account.accountNo, input.stockCode, input.quantity, `LOCAL-${Date.now()}`);
      if (!freezeResult.ok) return setMessage(message, freezeResult.message || "股票冻结失败", "error");
      if (!centralOwnsAssets) holding.sellable -= input.quantity;
    }

    const centralResult = await submitOrderToCentral(orderDraft);
    if (!centralResult.ok) {
      if (side === "buy") {
        const amount = input.orderPrice * input.quantity;
        if (!centralOwnsAssets) {
          account.availableCash += amount;
          account.frozenCash -= amount;
          await releaseFunds(account.accountNo, amount, "CENTRAL_REJECT");
        }
      } else {
        const holding = account.holdings.find((item) => item.stockCode === input.stockCode);
        if (!centralOwnsAssets) {
          if (holding) holding.sellable += input.quantity;
          await releaseHolding(account.accountNo, input.stockCode, input.quantity, "CENTRAL_REJECT");
        }
      }
      saveState();
      renderAll();
      return setMessage(message, centralResult.message || "中央交易系统拒绝委托，冻结资源已释放", "error");
    }

    const order = {
      id: centralResult.orderNo,
      stockCode: input.stockCode,
      stockName: input.stock.name,
      side,
      price: input.orderPrice,
      quantity: input.quantity,
      tradedQuantity: 0,
      remainingQuantity: input.quantity,
      status: centralResult.status || "未成交",
      submitTime: nowText(),
    };
    const clientOrderResult = await createClientOrder(order, account);
    if (clientOrderResult.ok && clientOrderResult.localOrderId) {
      order.localOrderId = clientOrderResult.localOrderId;
    } else if (!clientOrderResult.ok) {
      toast(clientOrderResult.message || "委托记录写入交易客户端数据库失败");
    }
    state.orders.unshift(order);
    saveState();
    form.reset();
    setMessage(message, `委托已提交，编号 ${order.id}`, "ok");
    toast("委托已提交");
    renderAll();
  } finally {
    operationLocks.delete(lockKey);
    if (submitButton) {
      submitButton.disabled = false;
      submitButton.textContent = previousButtonText;
    }
  }
}

async function cancelOrder(orderId) {
  if (!validateSession()) return;
  const lockKey = `cancel:${orderId}`;
  if (operationLocks.has(lockKey)) return;
  operationLocks.add(lockKey);
  const order = state.orders.find((item) => item.id === orderId);
  if (!order || !["未成交", "部分成交"].includes(order.status)) {
    operationLocks.delete(lockKey);
    toast("指令已成交或已撤销，无法撤销");
    return;
  }
  const account = currentAccount();
  const previousStatus = order.status;
  const previousRemainingQuantity = order.remainingQuantity;
  order.status = "撤单中";
  await updateClientOrder(order);
  saveState();
  renderAll();

  const centralResult = await cancelOrderInCentral(orderId);
  if (!centralResult.ok) {
    order.status = previousStatus;
    order.remainingQuantity = previousRemainingQuantity;
    await updateClientOrder(order);
    saveState();
    renderAll();
    operationLocks.delete(lockKey);
    toast(centralResult.message || "中央交易系统撤销失败");
    return;
  }
  if (centralTradingManagesAssets()) {
    const synced = await syncOrderResult(orderId, { silent: true });
    if (!synced || state.orders.find((item) => item.id === orderId)?.status === "撤单中") {
      toast("撤单请求已提交，等待中央交易系统回报");
    }
    operationLocks.delete(lockKey);
    return;
  }

  if (!centralTradingManagesAssets()) {
    if (order.side === "buy") {
      const release = order.remainingQuantity * order.price;
      await releaseFunds(account.accountNo, release, order.id);
      account.frozenCash -= release;
      account.availableCash += release;
    } else {
      await releaseHolding(account.accountNo, order.stockCode, order.remainingQuantity, order.id);
      const holding = account.holdings.find((item) => item.stockCode === order.stockCode);
      if (holding) holding.sellable += order.remainingQuantity;
    }
  }
  order.status = "已撤销";
  order.remainingQuantity = 0;
  await updateClientOrder(order);
  saveState();
  toast("撤销成功，冻结资源已释放");
  renderAll();
  operationLocks.delete(lockKey);
}

async function simulateTrade(orderId) {
  if (!validateSession()) return;
  if (API_CONFIG.centralBaseUrl || (API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled)) {
    await syncOrderResult(orderId, { silent: false });
    return;
  }
  const order = state.orders.find((item) => item.id === orderId);
  if (!order || order.status !== "未成交") return;
  const account = currentAccount();
  order.tradedQuantity = order.quantity;
  order.remainingQuantity = 0;
  order.status = "已成交";
  const amount = order.quantity * order.price;

  if (order.side === "buy") {
    account.frozenCash -= amount;
    const holding = account.holdings.find((item) => item.stockCode === order.stockCode);
    if (holding) {
      const totalCost = holding.cost * holding.quantity + amount;
      holding.quantity += order.quantity;
      holding.sellable += order.quantity;
      holding.cost = totalCost / holding.quantity;
    } else {
      account.holdings.push({ stockCode: order.stockCode, quantity: order.quantity, sellable: order.quantity, cost: order.price });
    }
  } else {
    account.availableCash += amount;
    const holding = account.holdings.find((item) => item.stockCode === order.stockCode);
    if (holding) holding.quantity -= order.quantity;
    account.holdings = account.holdings.filter((item) => item.quantity > 0);
  }

  const trade = {
    id: `T${Date.now()}`,
    orderId: order.id,
    stockCode: order.stockCode,
    stockName: order.stockName,
    price: order.price,
    quantity: order.quantity,
    amount,
    time: nowText(),
  };
  state.trades.unshift(trade);
  await createClientTrade(trade, order);
  await updateClientOrder(order);
  saveState();
  toast("成交回报已接收，资产与持仓已刷新");
  renderAll();
}

async function syncOrderResult(orderId, { silent = false } = {}) {
  const lockKey = `sync:${orderId}`;
  if (operationLocks.has(lockKey)) return false;
  operationLocks.add(lockKey);
  try {
    const result = await fetchOrderResultFromCentral(orderId);
    if (!result.ok) {
      if (!silent) toast(result.message || "中央交易系统暂无成交回报");
      return false;
    }
    return applyCentralTradeResult(orderId, result.result, { silent });
  } finally {
    operationLocks.delete(lockKey);
  }
}

async function syncOpenOrders({ silent = true } = {}) {
  if (!state.session || !(API_CONFIG.centralBaseUrl || (API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled))) {
    return;
  }
  const openOrders = state.orders.filter((order) => ORDER_OPEN_STATUSES.includes(order.status));
  for (const order of openOrders) {
    await syncOrderResult(order.id, { silent });
  }
}

async function applyCentralTradeResult(orderId, result, { silent = false } = {}) {
  const order = state.orders.find((item) => item.id === orderId);
  if (!order || !result) return false;
  const account = currentAccount();
  const previousTradedQuantity = Number(order.tradedQuantity || 0);
  const previousStatus = order.status;
  const previousRemainingQuantity = Number(order.remainingQuantity || 0);
  const normalizedStatus = result.status ? normalizeOrderStatus(result.status) : "";
  const tradedQuantity = Number(
    result.tradedQuantity ??
      result.filledQuantity ??
      result.quantity ??
      (normalizedStatus === "已成交" ? order.quantity : previousTradedQuantity),
  );
  const tradePrice = Number(result.tradePrice ?? result.price ?? order.price);
  const incrementalQuantity = Math.max(0, tradedQuantity - previousTradedQuantity);
  const amount = incrementalQuantity * tradePrice;
  order.tradedQuantity = tradedQuantity;
  order.remainingQuantity = Number(
    result.remainingQuantity ??
      (["已撤销", "已过期", "已拒绝"].includes(normalizedStatus)
        ? 0
        : Math.max(0, order.quantity - tradedQuantity)),
  );
  order.status = normalizedStatus || (order.remainingQuantity === 0 ? "已成交" : "部分成交");

  if (!centralTradingManagesAssets() && incrementalQuantity > 0 && order.side === "buy") {
    account.frozenCash -= amount;
    const holding = account.holdings.find((item) => item.stockCode === order.stockCode);
    if (holding) {
      const totalCost = holding.cost * holding.quantity + amount;
      holding.quantity += incrementalQuantity;
      holding.sellable += incrementalQuantity;
      holding.cost = totalCost / holding.quantity;
    } else {
      account.holdings.push({ stockCode: order.stockCode, quantity: incrementalQuantity, sellable: incrementalQuantity, cost: tradePrice });
    }
  } else if (!centralTradingManagesAssets() && incrementalQuantity > 0) {
    account.availableCash += amount;
    const holding = account.holdings.find((item) => item.stockCode === order.stockCode);
    if (holding) holding.quantity -= incrementalQuantity;
    account.holdings = account.holdings.filter((item) => item.quantity > 0);
  }

  if (incrementalQuantity > 0) {
    const reportTrades = Array.isArray(result.trades) && result.trades.length
      ? result.trades
      : [{
          tradeNo: result.tradeNo || result.tradeId,
          tradePrice,
          tradeQuantity: incrementalQuantity,
          tradeAmount: amount,
          tradeTime: result.tradeTime,
        }];

    for (const item of reportTrades) {
      const trade = {
        id: item.tradeNo || item.tradeId || `T${Date.now()}${Math.floor(Math.random() * 1000)}`,
        orderId: order.id,
        stockCode: item.stockCode || order.stockCode,
        stockName: order.stockName,
        price: Number(item.tradePrice ?? tradePrice),
        quantity: Number(item.tradeQuantity ?? incrementalQuantity),
        amount: Number(item.tradeAmount ?? Number(item.tradePrice ?? tradePrice) * Number(item.tradeQuantity ?? incrementalQuantity)),
        time: item.tradeTime || result.tradeTime || nowText(),
      };
      if (!state.trades.some((existing) => existing.id === trade.id && existing.orderId === trade.orderId)) {
        state.trades.unshift(trade);
        await createClientTrade(trade, order);
      }
    }
  }
  await updateClientOrder(order);
  saveState();
  const changed =
    incrementalQuantity > 0 ||
    previousStatus !== order.status ||
    previousRemainingQuantity !== Number(order.remainingQuantity || 0);
  if (changed && !silent) toast("中央交易系统订单回报已同步");
  renderAll();
  return changed;
}

async function createAlert(form) {
  if (!validateSession()) return;
  const message = form.querySelector(".form-message");
  const stockCode = form.stockCode.value.trim();
  const stock = state.stocks[stockCode];
  const alertPrice = Number(form.price.value.trim());
  if (!/^\d{6}$/.test(stockCode) || !stock) return setMessage(message, "股票代码无效", "error");
  if (!/^\d+(\.\d{1,2})?$/.test(form.price.value.trim()) || alertPrice <= 0) return setMessage(message, "提醒价格必须大于0，且最多保留2位小数", "error");
  const account = currentAccount();
  const alert = {
    id: `A${Date.now()}`,
    stockCode,
    stockName: stock.name,
    direction: form.direction.value,
    price: alertPrice,
    status: "启用",
    createTime: nowText(),
    triggerTime: "",
  };
  const clientAlertResult = await createClientAlert(alert, account);
  if (clientAlertResult.ok && clientAlertResult.alertId) {
    alert.alertId = clientAlertResult.alertId;
  } else if (!clientAlertResult.ok) {
    toast(clientAlertResult.message || "价格提醒写入交易客户端数据库失败");
  }
  state.alerts.unshift(alert);
  saveState();
  form.reset();
  setMessage(message, "提醒规则已保存", "ok");
  await checkAlerts();
  renderAlerts();
}

async function checkAlerts() {
  for (const alert of state.alerts) {
    if (alert.status !== "启用") continue;
    const stock = state.stocks[alert.stockCode];
    if (!stock) continue;
    const matched = alert.direction === "ABOVE" ? stock.latest >= alert.price : stock.latest <= alert.price;
    if (matched) {
      alert.status = "已触发";
      alert.triggerTime = nowText();
      const content = `${alert.stockName} 已触发价格提醒`;
      await updateClientAlert(alert, { alertStatus: "TRIGGERED", triggerNow: true });
      const notificationResult = await createClientNotification(alert, content);
      const notification = {
        id: notificationResult.notificationId ? `N${notificationResult.notificationId}` : `N${Date.now()}`,
        notificationId: notificationResult.notificationId,
        alertId: alert.alertId,
        content,
        readStatus: "UNREAD",
        time: nowText(),
      };
      if (!state.notifications.some((item) => item.id === notification.id)) {
        state.notifications.unshift(notification);
      }
      toast(content);
    }
  }
  saveState();
}

async function refreshAlertMonitor({ silent = true } = {}) {
  if (!state.session) return;
  const activeAlerts = state.alerts.filter((alert) => alert.status === "启用");
  if (!activeAlerts.length) return;

  const stockCodes = [...new Set(activeAlerts.map((alert) => alert.stockCode))];
  for (const stockCode of stockCodes) {
    const quoteResult = await fetchQuotes(stockCode);
    if (quoteResult.ok) {
      quoteResult.stocks.forEach((stock) => {
        state.stocks[stock.stockCode] = stock;
      });
    } else if (!silent) {
      toast(quoteResult.message || "价格提醒行情检查失败");
    }
  }
  await checkAlerts();
  renderAll();
}

async function refreshClientNotifications({ silent = true } = {}) {
  const account = currentAccount();
  if (!account || !API_CONFIG.clientBaseUrl) return;

  const result = await fetchClientNotifications(account);
  if (!result.ok) {
    if (!silent) toast(result.message || "通知刷新失败");
    return;
  }
  if (!result.mock) state.notifications = result.notifications;
  saveState();
  renderNotifications();
}

async function markNotificationRead(notificationId) {
  const notification = state.notifications.find((item) => String(item.id) === String(notificationId));
  if (!notification || notification.readStatus === "READ") return;

  const result = await markClientNotificationRead(notification.notificationId);
  if (!result.ok) {
    toast(result.message || "通知状态更新失败");
    return;
  }
  notification.readStatus = "READ";
  saveState();
  renderNotifications();
}

function parseAmount(rawValue) {
  const raw = String(rawValue || "").trim();
  if (!/^\d+(\.\d{1,2})?$/.test(raw) || Number(raw) <= 0) return null;
  return Number(raw);
}

function updateDisplayedBalance(result, fallbackDelta) {
  const account = currentAccount();
  const returnedBalance = Number(result.data?.available_balance);
  account.availableCash = Number.isFinite(returnedBalance)
    ? returnedBalance
    : account.availableCash + fallbackDelta;
  saveState();
  renderAccount();
}

async function depositFunds(form) {
  if (!validateSession()) return;
  const message = form.querySelector(".form-message");
  const amount = parseAmount(form.amount.value);
  if (!amount) return setMessage(message, "请输入大于 0、最多两位小数的存款金额", "error");
  const result = await depositViaAccountSystem(currentAccount().accountNo, amount);
  if (!result.ok) return setMessage(message, result.message || "账户系统存款失败", "error");
  form.reset();
  updateDisplayedBalance(result, amount);
  setMessage(message, "存款成功，余额已与账户系统同步", "ok");
}

async function withdrawFunds(form) {
  if (!validateSession()) return;
  const message = form.querySelector(".form-message");
  const amount = parseAmount(form.amount.value);
  const withdrawPassword = form.withdrawPassword.value.trim();
  if (!amount) return setMessage(message, "请输入大于 0、最多两位小数的取款金额", "error");
  if (!/^\d{6}$/.test(withdrawPassword)) return setMessage(message, "取款密码必须为 6 位数字", "error");
  const result = await withdrawViaAccountSystem(currentAccount().accountNo, amount, withdrawPassword);
  if (!result.ok) return setMessage(message, result.message || "账户系统取款失败", "error");
  form.reset();
  updateDisplayedBalance(result, -amount);
  setMessage(message, "取款成功，余额已与账户系统同步", "ok");
}

async function deleteAlert(alertId) {
  const alert = state.alerts.find((item) => item.id === alertId);
  if (alert) {
    alert.status = "停用";
    await updateClientAlert(alert, { alertStatus: "DISABLED" });
  }
  state.alerts = state.alerts.filter((item) => item.id !== alertId);
  saveState();
  renderAlerts();
}

async function changePassword(form) {
  if (!validateSession()) return;
  const account = currentAccount();
  const message = form.querySelector(".form-message");
  const type = form.type.value;
  const oldPassword = form.oldPassword.value.trim();
  const newPassword = form.newPassword.value.trim();
  const confirmPassword = form.confirmPassword.value.trim();
  const key = type === "trade" ? "tradePassword" : "withdrawPassword";
  if (API_CONFIG.accountBaseUrl) account[key] = oldPassword;
  if (oldPassword !== account[key]) return setMessage(message, "原密码错误，请重新输入", "error");
  if (!/^\d{6}$/.test(newPassword) || /^(\d)\1{5}$/.test(newPassword)) return setMessage(message, "密码格式不符合要求", "error");
  if (newPassword === oldPassword) return setMessage(message, "新密码不能与原密码相同", "error");
  if (newPassword !== confirmPassword) return setMessage(message, "两次输入的密码不一致，请重新输入", "error");
  const result = await changePasswordViaAccountSystem(account.accountNo, type, oldPassword, newPassword);
  if (!result.ok) return setMessage(message, result.message || "资金账户系统修改密码失败", "error");
  account[key] = newPassword;
  saveState();
  form.reset();
  setMessage(message, "密码修改成功", "ok");
}
