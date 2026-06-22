function showTerminal() {
  dom.loginView.classList.add("hidden");
  dom.terminalView.classList.remove("hidden");
  setMessage(dom.loginMessage, "");
  refreshExternalData().then(() => renderAll());
}

function renderAll() {
  renderAccount();
  renderHoldings();
  renderOrders();
  renderTrades();
  renderAlerts();
  renderNotifications();
  dom.clockText.textContent = nowText();
  const account = currentAccount();
  dom.sessionAccount.textContent = account ? `${account.name} ${account.accountNo}` : "未登录";
}

function renderAccount() {
  const account = currentAccount();
  if (!account) return;
  const holdingValue = account.holdings.reduce((sum, item) => {
    const stock = state.stocks[item.stockCode];
    return stock ? sum + item.quantity * stock.latest : sum;
  }, 0);
  dom.availableCash.textContent = money(account.availableCash);
  dom.frozenCash.textContent = money(account.frozenCash);
  dom.totalAsset.textContent = money(account.availableCash + account.frozenCash + holdingValue);
  dom.accountStatus.textContent = account.status;
}

function renderHoldings() {
  const account = currentAccount();
  if (!account) return;
  dom.holdingRows.innerHTML = "";
  account.holdings.forEach((item) => {
    const stock = state.stocks[item.stockCode] || { name: item.stockCode, latest: 0 };
    const profit = (stock.latest - item.cost) * item.quantity;
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${item.stockCode}</td>
      <td>${stock.name}</td>
      <td>${item.quantity}</td>
      <td>${item.sellable}</td>
      <td>${money(item.cost)}</td>
      <td>${money(stock.latest)}</td>
      <td class="${profit >= 0 ? "gain" : "loss"}">${money(profit)}</td>
    `;
    dom.holdingRows.appendChild(row);
  });
  dom.holdingSummary.textContent = account.holdings.length ? `${account.holdings.length} 只股票` : "暂无持仓";
}

function renderMarket(stocks) {
  dom.marketResult.innerHTML = "";
  stocks.forEach((stock) => {
    const limits = getLimits(stock);
    const change = stock.latest - stock.prevClose;
    const changeRate = stock.prevClose ? (change / stock.prevClose) * 100 : 0;
    const card = document.createElement("article");
    card.className = "quote-card";
    card.innerHTML = `
      <div class="panel-header">
        <h3>${stock.name} ${stock.stockCode}</h3>
        <span>${stock.status}</span>
      </div>
      <div class="quote-grid">
        <div><span>最新价</span><strong>${money(stock.latest)}</strong></div>
        <div><span>涨跌幅</span><strong class="${change >= 0 ? "gain" : "loss"}">${changeRate.toFixed(2)}%</strong></div>
        <div><span>买一 / 卖一</span><strong>${price(stock.buyOne)} / ${price(stock.sellOne)}</strong></div>
        <div><span>最高 / 最低</span><strong>${price(stock.high)} / ${price(stock.low)}</strong></div>
        <div><span>涨停 / 跌停</span><strong>${price(limits.upper)} / ${price(limits.lower)}</strong></div>
      </div>
      <p class="hint">公告：${stock.announcement || "暂无公告"}</p>
    `;
    dom.marketResult.appendChild(card);
  });
}

function renderOrders() {
  dom.orderRows.innerHTML = "";
  state.orders.forEach((order) => {
    const centralMode = API_CONFIG.centralBaseUrl || (API_CONFIG.clientBaseUrl && API_CONFIG.centralKafkaEnabled);
    const canCancel = ["未成交", "部分成交"].includes(order.status);
    const canSync = centralMode
      ? ["未成交", "部分成交", "撤单中"].includes(order.status)
      : order.status === "未成交";
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${order.id}</td>
      <td>${order.stockName} ${order.stockCode}</td>
      <td>${order.side === "buy" ? "买入" : "卖出"}</td>
      <td>${money(order.price)}</td>
      <td>${order.quantity}</td>
      <td>${order.tradedQuantity}</td>
      <td>${order.remainingQuantity}</td>
      <td>${order.status}</td>
      <td>
        <button class="secondary-btn" data-fill="${order.id}" ${canSync ? "" : "disabled"}>${centralMode ? "同步" : "成交"}</button>
        <button class="ghost-btn" data-cancel="${order.id}" ${canCancel ? "" : "disabled"}>撤销</button>
      </td>
    `;
    dom.orderRows.appendChild(row);
  });
  if (!state.orders.length) dom.orderRows.innerHTML = `<tr><td colspan="9">暂无委托记录</td></tr>`;
}

function renderTrades() {
  dom.tradeRows.innerHTML = "";
  state.trades.forEach((trade) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${trade.id}</td>
      <td>${trade.orderId}</td>
      <td>${trade.stockName} ${trade.stockCode}</td>
      <td>${money(trade.price)}</td>
      <td>${trade.quantity}</td>
      <td>${money(trade.amount)}</td>
      <td>${trade.time}</td>
    `;
    dom.tradeRows.appendChild(row);
  });
  if (!state.trades.length) dom.tradeRows.innerHTML = `<tr><td colspan="7">暂无成交结果</td></tr>`;
}

function renderAlerts() {
  dom.alertRows.innerHTML = "";
  state.alerts.forEach((alert) => {
    const item = document.createElement("article");
    item.className = "alert-item";
    item.innerHTML = `
      <div>
        <strong>${alert.stockName} ${alert.stockCode}</strong>
        <p class="hint">${alert.direction === "ABOVE" ? "高于或等于" : "低于或等于"} ${money(alert.price)} · ${alert.status} · ${alert.triggerTime || alert.createTime}</p>
      </div>
      <button class="ghost-btn" data-alert-delete="${alert.id}">删除</button>
    `;
    dom.alertRows.appendChild(item);
  });
  if (!state.alerts.length) dom.alertRows.innerHTML = `<p class="hint">暂无价格提醒</p>`;
  dom.alertSummary.textContent = `${state.alerts.length} 条规则`;
}

function renderNotifications() {
  if (!dom.notificationRows || !dom.notificationSummary) return;
  dom.notificationRows.innerHTML = "";
  state.notifications.forEach((notification) => {
    const item = document.createElement("article");
    item.className = `alert-item ${notification.readStatus === "UNREAD" ? "notification-unread" : ""}`.trim();
    item.innerHTML = `
      <div>
        <strong>${notification.readStatus === "UNREAD" ? "未读通知" : "已读通知"}</strong>
        <p class="hint">${notification.content} · ${notification.time || ""}</p>
      </div>
      <button class="ghost-btn" data-notification-read="${notification.id}" ${notification.readStatus === "READ" ? "disabled" : ""}>标为已读</button>
    `;
    dom.notificationRows.appendChild(item);
  });
  if (!state.notifications.length) dom.notificationRows.innerHTML = `<p class="hint">暂无通知</p>`;
  const unreadCount = state.notifications.filter((item) => item.readStatus === "UNREAD").length;
  dom.notificationSummary.textContent = `${unreadCount} 条未读 / ${state.notifications.length} 条通知`;
}

function setView(viewId) {
  if (!validateSession()) return;
  dom.navItems.forEach((item) => item.classList.toggle("active", item.dataset.view === viewId));
  dom.views.forEach((view) => view.classList.toggle("active-view", view.id === viewId));
  const active = [...dom.navItems].find((item) => item.dataset.view === viewId);
  dom.viewTitle.textContent = active ? active.textContent : "账户资产";
}
