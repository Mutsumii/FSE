dom.loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const submitButton = dom.loginForm.querySelector("button[type='submit']");
  submitButton.disabled = true;
  submitButton.textContent = "正在登录";
  const result = await login(dom.accountNo.value.trim(), dom.tradePassword.value.trim());
  submitButton.disabled = false;
  submitButton.textContent = "登录";
  if (!result.ok) return setMessage(dom.loginMessage, result.message, "error");
  if (result.requiresCertificate) {
    dom.certificateSection.classList.remove("hidden");
    dom.certificateCode.focus();
    return setMessage(dom.loginMessage, result.message, "ok");
  }
  setMessage(dom.loginMessage, result.message, "ok");
  if (result.redirected) return;
  showTerminal();
  startClientBackgroundJobs();
});

dom.certificateSubmitBtn.addEventListener("click", async () => {
  dom.certificateSubmitBtn.disabled = true;
  dom.certificateSubmitBtn.textContent = "认证中";
  const result = await completeInvestorCertificate(dom.certificateCode.value.trim());
  dom.certificateSubmitBtn.disabled = false;
  dom.certificateSubmitBtn.textContent = "完成证书认证";
  if (!result.ok) return setMessage(dom.loginMessage, result.message, "error");
  dom.certificateSection.classList.add("hidden");
  dom.certificateCode.value = "";
  setMessage(dom.loginMessage, result.message, "ok");
  if (result.redirected) return;
  showTerminal();
  startClientBackgroundJobs();
});

dom.logoutBtn.addEventListener("click", () => logout());

dom.navItems.forEach((item) => item.addEventListener("click", () => setView(item.dataset.view)));

dom.refreshBtn.addEventListener("click", async () => {
  if (!validateSession()) return;
  await refreshExternalData({ randomizeMockQuotes: true });
  await restoreClientState({ silent: true });
  await syncOpenOrders({ silent: true });
  toast("行情与账户数据已刷新");
});

let marketQueryTimer = null;

dom.marketForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!validateSession()) return;
  clearTimeout(marketQueryTimer);
  await runMarketQuery(dom.marketKeyword.value.trim(), { autoConfirm: true });
});

async function runMarketQuery(keyword, { autoConfirm = false } = {}) {
  const result = await fetchQuotes(keyword);
  if (!result.ok) {
    if (result.pending && autoConfirm && /^\d{6}$/.test(keyword)) {
      renderMarketNotice("正在向中央交易系统查询行情...", "pending");
      marketQueryTimer = setTimeout(() => {
        runMarketQuery(keyword, { autoConfirm: false });
      }, 8500);
      return;
    }
    renderMarketNotice(result.notFound ? "股票不存在" : result.message, "error");
    return;
  }
  result.stocks.forEach((stock) => {
    state.stocks[stock.stockCode] = stock;
  });
  saveState();
  renderMarket(result.stocks);
}

function renderMarketNotice(message, type = "error") {
  dom.marketResult.innerHTML = `
    <div class="market-notice ${type}">
      <strong>${message}</strong>
      <span>${type === "pending" ? "请稍候，系统会自动确认结果" : "请检查股票代码后重新查询"}</span>
    </div>
  `;
}

dom.buyForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await submitOrder(dom.buyForm, "buy");
});

dom.sellForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await submitOrder(dom.sellForm, "sell");
});

dom.orderRows.addEventListener("click", async (event) => {
  const cancelId = event.target.dataset.cancel;
  const fillId = event.target.dataset.fill;
  if (cancelId) await cancelOrder(cancelId);
  if (fillId) await simulateTrade(fillId);
});

dom.alertForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await createAlert(dom.alertForm);
});

dom.alertRows.addEventListener("click", async (event) => {
  const alertId = event.target.dataset.alertDelete;
  if (alertId) await deleteAlert(alertId);
});

dom.notificationRows.addEventListener("click", async (event) => {
  const notificationId = event.target.dataset.notificationRead;
  if (notificationId) await markNotificationRead(notificationId);
});

dom.passwordForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await changePassword(dom.passwordForm);
});

dom.depositForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await depositFunds(dom.depositForm);
});

dom.withdrawForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await withdrawFunds(dom.withdrawForm);
});

setInterval(() => {
  dom.clockText.textContent = nowText();
}, 1000);

bootSession();
