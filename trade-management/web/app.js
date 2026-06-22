const state = {
    token: localStorage.getItem("managementToken"),
    role: localStorage.getItem("managementRole"),
    username: localStorage.getItem("managementUsername"),
    selectedStock: null,
    authMode: "login",
    dialogResolver: null
};

const $ = (id) => document.getElementById(id);

async function api(path, options = {}) {
    const headers = {
        "Content-Type": "application/json",
        ...(options.headers || {})
    };
    if (state.token) {
        headers.Authorization = `Bearer ${state.token}`;
    }
    const response = await fetch(path, { ...options, headers });
    const body = await response.json();
    if (!response.ok || !body.success) {
        throw new Error(body.message || "请求失败");
    }
    return body.data;
}

function setIdentity() {
    $("currentAdminName").textContent = state.username || "";
}

function showDashboard() {
    $("loginView").classList.add("hidden");
    $("dashboardView").classList.remove("hidden");
    $("adminIdentity").classList.remove("hidden");
    setIdentity();
    const isSuper = state.role === "SUPER_ADMIN";
    $("adminUsersSection").classList.toggle("hidden", !isSuper);
    $("auditSection").classList.toggle("hidden", !isSuper);
}

function showLogin() {
    $("loginView").classList.remove("hidden");
    $("dashboardView").classList.add("hidden");
    $("adminIdentity").classList.add("hidden");
}

function persistLogin(data) {
    state.token = data.token;
    state.role = data.role;
    state.username = data.username;
    localStorage.setItem("managementToken", data.token);
    localStorage.setItem("managementRole", data.role);
    localStorage.setItem("managementUsername", data.username);
}

function logout() {
    state.token = null;
    state.role = null;
    state.username = null;
    localStorage.removeItem("managementToken");
    localStorage.removeItem("managementRole");
    localStorage.removeItem("managementUsername");
    showLogin();
}

async function login() {
    $("loginMessage").textContent = "";
    try {
        const data = await api("/api/admin/login", {
            method: "POST",
            body: JSON.stringify({
                username: $("username").value.trim(),
                password: $("password").value
            })
        });
        persistLogin(data);
        showDashboard();
        await loadAll();
    } catch (error) {
        $("loginMessage").textContent = error.message;
    }
}

async function register() {
    $("loginMessage").textContent = "";
    try {
        const data = await api("/api/admin/register", {
            method: "POST",
            body: JSON.stringify({
                username: $("username").value.trim(),
                password: $("password").value,
                confirmPassword: $("confirmPassword").value
            })
        });
        persistLogin(data);
        showDashboard();
        await loadAll();
    } catch (error) {
        $("loginMessage").textContent = error.message;
    }
}

function setAuthMode(mode) {
    state.authMode = mode;
    const isLogin = mode === "login";
    $("authTitle").textContent = isLogin ? "管理员登录" : "管理员注册";
    $("confirmPasswordLabel").classList.toggle("hidden", isLogin);
    $("loginBtn").classList.toggle("hidden", !isLogin);
    $("registerBtn").classList.toggle("hidden", isLogin);
    $("showLoginBtn").classList.toggle("active", isLogin);
    $("showRegisterBtn").classList.toggle("active", !isLogin);
    $("username").value = isLogin ? "admin" : "";
    $("password").value = isLogin ? "admin123" : "";
    $("confirmPassword").value = "";
    $("loginMessage").textContent = "";
}

function showDialog(title, message, withInput = false, password = false) {
    $("dialogTitle").textContent = title;
    $("dialogMessage").textContent = message;
    $("dialogInput").classList.toggle("hidden", !withInput);
    $("dialogInput").type = password ? "password" : "text";
    $("dialogInput").value = "";
    $("appDialog").showModal();
    if (withInput) {
        $("dialogInput").focus();
    }
    return new Promise(resolve => {
        state.dialogResolver = resolve;
    });
}

function finishDialog(value) {
    $("appDialog").close();
    if (state.dialogResolver) {
        state.dialogResolver(value);
        state.dialogResolver = null;
    }
}

function showAlert(message) {
    return showDialog("提示", message, false, false);
}

function showConfirm(message, title = "确认") {
    return showDialog(title, message, false, false);
}

function showPrompt(message, password = false) {
    return showDialog("请输入", message, true, password);
}

async function changePassword() {
    $("passwordDialogForm").reset();
    $("passwordDialogError").classList.add("hidden");
    $("passwordDialog").showModal();
    $("currentPasswordInput").focus();
}

async function deleteAccount() {
    if (!await showConfirm("注销后账号及股票权限将被永久删除，确认继续？", "注销不可撤回")) {
        return;
    }
    const password = await showPrompt("请输入当前密码确认注销", true);
    if (password === null) {
        return;
    }
    try {
        await api("/api/admin/account", {
            method: "DELETE",
            body: JSON.stringify({ password })
        });
        await showAlert("管理员账号已注销");
        logout();
    } catch (error) {
        await showAlert(`账号注销失败：${error.message}`);
    }
}

async function loadStocks() {
    const stocks = await api("/api/admin/stocks");
    $("stockList").innerHTML = stocks.map(stock => `
        <div class="stock-card ${state.selectedStock === stock.stockCode ? "active" : ""}" data-code="${stock.stockCode}">
            <div class="stock-name">
                <span>${stock.stockName}</span>
                <span>${stock.stockCode}</span>
            </div>
            <div class="stock-meta">最新价 ${stock.lastPrice ?? "-"} | ${stock.status === "TRADING" ? "交易中" : "已暂停"}</div>
        </div>
    `).join("");
    document.querySelectorAll(".stock-card").forEach(card => {
        card.addEventListener("click", () => selectStock(card.dataset.code));
    });
}

function orderTable(title, orders) {
    const rows = orders.map(order => `
        <tr>
            <td>${order.orderId}</td>
            <td>${order.price}</td>
            <td>${order.quantity}</td>
            <td>${String(order.enteredAt).replace("T", " ")}</td>
        </tr>
    `).join("");
    return `
        <div>
            <h2>${title}</h2>
            <table>
                <thead><tr><th>委托号</th><th>价格</th><th>股数</th><th>进入时间</th></tr></thead>
                <tbody>${rows || "<tr><td colspan='4'>暂无指令</td></tr>"}</tbody>
            </table>
        </div>
    `;
}

async function selectStock(stockCode) {
    state.selectedStock = stockCode;
    await loadStocks();
    const data = await api(`/api/admin/stocks/${stockCode}/orders`);
    $("stockDetail").classList.remove("empty");
    $("stockDetail").innerHTML = `
        <div class="detail-head">
            <div>
                <h2>${data.stockName} ${data.stockCode}</h2>
                <div>最新成交价 ${data.lastPrice ?? "-"}，最新成交数量 ${data.lastQuantity ?? 0}</div>
            </div>
            <span class="status ${data.status === "TRADING" ? "trading" : "paused"}">${data.status === "TRADING" ? "交易中" : "已暂停"}</span>
        </div>
        <div class="actions">
            <button data-action="pause">暂停交易</button>
            <button data-action="resume" class="ghost">恢复交易</button>
            <div class="limit-control">
                <input id="limitRateInput" placeholder="次日涨跌幅 如 0.10">
                <button data-action="limit">设置涨跌停</button>
            </div>
        </div>
        <div class="order-grid">
            ${orderTable("买指令", data.buyOrders || [])}
            ${orderTable("卖指令", data.sellOrders || [])}
        </div>
    `;
    $("stockDetail").querySelector("[data-action='pause']").addEventListener("click", () => updateStockStatus("pause"));
    $("stockDetail").querySelector("[data-action='resume']").addEventListener("click", () => updateStockStatus("resume"));
    $("stockDetail").querySelector("[data-action='limit']").addEventListener("click", updateLimitRate);
}

async function updateStockStatus(action) {
    await api(`/api/admin/stocks/${state.selectedStock}/${action}`, { method: "POST" });
    await selectStock(state.selectedStock);
}

async function updateLimitRate() {
    const nextLimitRate = $("limitRateInput").value.trim();
    if (!nextLimitRate) {
        await showAlert("请输入次日涨跌幅");
        return;
    }
    await api(`/api/admin/stocks/${state.selectedStock}/limit-rate`, {
        method: "POST",
        body: JSON.stringify({ nextLimitRate })
    });
    await showAlert("设置成功，次日生效");
}

async function loadBlacklist() {
    const list = await api("/api/admin/blacklist");
    $("blacklistTable").innerHTML = `
        <table>
            <thead><tr><th>证件号</th><th>姓名</th><th>资金账户</th><th>证券账户</th><th>原因</th><th>操作</th></tr></thead>
            <tbody>
            ${list.map(item => `
                <tr>
                    <td>${item.idCardNo}</td>
                    <td>${item.userName}</td>
                    <td>${item.fundAccountNo || ""}</td>
                    <td>${item.securityAccountNo || ""}</td>
                    <td>${item.reason || ""}</td>
                    <td><button class="ghost" data-remove="${item.idCardNo}">移除</button></td>
                </tr>
            `).join("") || "<tr><td colspan='6'>暂无黑名单</td></tr>"}
            </tbody>
        </table>
    `;
    document.querySelectorAll("[data-remove]").forEach(button => {
        button.addEventListener("click", async () => {
            try {
                await api(`/api/admin/blacklist/${encodeURIComponent(button.dataset.remove)}`, { method: "DELETE" });
                await loadBlacklist();
            } catch (error) {
                await showAlert(`移出黑名单失败：${error.message}`);
            }
        });
    });
}

async function loadPendingReviews() {
    const list = await api("/api/admin/reviews/pending");
    $("reviewTable").innerHTML = `
        <table>
            <thead>
                <tr>
                    <th>审查号</th>
                    <th>投资者</th>
                    <th>股票</th>
                    <th>方向</th>
                    <th>金额</th>
                    <th>原因</th>
                    <th>操作</th>
                </tr>
            </thead>
            <tbody>
            ${list.map(item => `
                <tr>
                    <td>${item.reviewId}</td>
                    <td>${item.userName || item.fundAccountNo}</td>
                    <td>${item.stockName ? `${item.stockName} ` : ""}${item.stockCode}</td>
                    <td>${item.side === "BUY" ? "买入" : "卖出"}</td>
                    <td>${item.amount}</td>
                    <td>${item.reason || ""}</td>
                    <td>
                        <div class="review-actions">
                            <button data-review-action="approve" data-review-id="${item.reviewId}">通过</button>
                            <button class="ghost" data-review-action="reject" data-review-id="${item.reviewId}">拒绝</button>
                        </div>
                    </td>
                </tr>
            `).join("") || "<tr><td colspan='7'>暂无待核验委托</td></tr>"}
            </tbody>
        </table>
    `;
    document.querySelectorAll("[data-review-action]").forEach(button => {
        button.addEventListener("click", async () => {
            const action = button.dataset.reviewAction;
            const reviewId = button.dataset.reviewId;
            const reason = await showPrompt(action === "approve" ? "请输入通过说明" : "请输入拒绝原因");
            if (reason === null) {
                return;
            }
            try {
                button.disabled = true;
                await api(`/api/admin/reviews/${encodeURIComponent(reviewId)}/${action}`, {
                    method: "POST",
                    body: JSON.stringify({ reason })
                });
                await showAlert(action === "approve" ? "人工核验已通过" : "人工核验已拒绝");
                await loadPendingReviews();
            } catch (error) {
                await showAlert(`人工核验提交失败：${error.message}`);
                button.disabled = false;
            }
        });
    });
}

async function addBlacklist(event) {
    event.preventDefault();
    await api("/api/admin/blacklist", {
        method: "POST",
        body: JSON.stringify({
            idCardNo: $("blackIdCardNo").value.trim(),
            userName: $("blackUserName").value.trim(),
            fundAccountNo: $("blackFundNo").value.trim(),
            securityAccountNo: $("blackSecurityNo").value.trim(),
            reason: $("blackReason").value.trim()
        })
    });
    event.target.reset();
    await loadBlacklist();
}

async function loadAdminTools() {
    if (state.role !== "SUPER_ADMIN") {
        return;
    }
    await Promise.all([loadAdminUsers(), loadAuditLogs()]);
}

async function loadAdminUsers() {
    const users = await api("/api/admin/users");
    $("adminUsersTable").innerHTML = users.map(user => `
        <div class="permission-row">
            <span class="admin-id">ID ${user.id}</span>
            <strong>${user.username}</strong>
            <select data-role="${user.id}">
                <option value="ADMIN" ${user.role === "ADMIN" ? "selected" : ""}>ADMIN</option>
                <option value="SUPER_ADMIN" ${user.role === "SUPER_ADMIN" ? "selected" : ""}>SUPER_ADMIN</option>
            </select>
            <input data-stocks="${user.id}"
                   value="${user.role === "SUPER_ADMIN" ? "全部股票" : (user.stockCodes || []).join(",")}"
                   placeholder="${user.role === "SUPER_ADMIN" ? "全部股票" : "股票代码，逗号分隔"}"
                   ${user.role === "SUPER_ADMIN" ? "disabled" : ""}>
            <button data-save-user="${user.id}">保存</button>
        </div>
    `).join("");
    document.querySelectorAll("[data-role]").forEach(select => {
        select.addEventListener("change", () => {
            const id = select.dataset.role;
            const input = document.querySelector(`[data-stocks='${id}']`);
            const isSuper = select.value === "SUPER_ADMIN";
            input.disabled = isSuper;
            input.value = isSuper ? "全部股票" : "";
            input.placeholder = isSuper ? "全部股票" : "股票代码，逗号分隔";
        });
    });
    document.querySelectorAll("[data-save-user]").forEach(button => {
        button.addEventListener("click", async () => {
            const id = button.dataset.saveUser;
            const role = document.querySelector(`[data-role='${id}']`).value;
            const stockCodes = role === "SUPER_ADMIN" ? [] : document.querySelector(`[data-stocks='${id}']`).value
                .split(",").map(value => value.trim()).filter(Boolean);
            try {
                await api(`/api/admin/users/${id}/permissions`, {
                    method: "POST",
                    body: JSON.stringify({ role, stockCodes })
                });
                await showAlert("权限保存成功");
            } catch (error) {
                await showAlert(`权限保存失败：${error.message}`);
            }
            await loadAdminUsers();
        });
    });
}

async function controlAccount(action) {
    try {
        const data = {
            accountType: $("accountType").value,
            accountNo: $("controlAccountNo").value.trim(),
            freezeType: $("freezeType").value,
            reason: $("freezeReason").value.trim()
        };
        if (!data.accountNo) {
            await showAlert("请输入账户号");
            return;
        }
        await api(`/api/admin/accounts/${action}`, { method: "POST", body: JSON.stringify(data) });
        await showAlert(action === "freeze" ? "冻结成功" : "解冻成功");
    } catch (error) {
        await showAlert(`操作失败：${error.message}`);
    }
}

async function loadAuditLogs() {
    const logs = await api("/api/admin/audit-logs");
    $("auditTable").innerHTML = `
        <table><thead><tr><th>时间</th><th>管理员</th><th>操作</th><th>目标</th><th>详情</th></tr></thead>
        <tbody>${logs.map(log => `<tr><td>${String(log.createdAt).replace("T", " ")}</td><td>${log.username || ""}</td><td>${log.action}</td><td>${log.targetType || ""} ${log.targetId || ""}</td><td>${log.detail || ""}</td></tr>`).join("") || "<tr><td colspan='5'>暂无日志</td></tr>"}</tbody></table>`;
}

async function loadAll() {
    await loadStocks();
    await loadPendingReviews();
    await loadBlacklist();
    await loadAdminTools();
}

$("loginBtn").addEventListener("click", login);
$("registerBtn").addEventListener("click", register);
$("showLoginBtn").addEventListener("click", () => setAuthMode("login"));
$("showRegisterBtn").addEventListener("click", () => setAuthMode("register"));
$("logoutBtn").addEventListener("click", logout);
$("changePasswordBtn").addEventListener("click", changePassword);
$("deleteAccountBtn").addEventListener("click", deleteAccount);
$("refreshBlacklistBtn").addEventListener("click", loadBlacklist);
$("refreshReviewsBtn").addEventListener("click", loadPendingReviews);
$("refreshUsersBtn").addEventListener("click", loadAdminUsers);
$("refreshAuditBtn").addEventListener("click", loadAuditLogs);
$("blacklistForm").addEventListener("submit", addBlacklist);
$("freezeAccountBtn").addEventListener("click", () => controlAccount("freeze"));
$("unfreezeAccountBtn").addEventListener("click", () => controlAccount("unfreeze"));

$("appDialogForm").addEventListener("submit", event => {
    event.preventDefault();
    const inputVisible = !$("dialogInput").classList.contains("hidden");
    finishDialog(inputVisible ? $("dialogInput").value : true);
});
$("dialogCancelBtn").addEventListener("click", () => finishDialog(null));
$("dialogCloseBtn").addEventListener("click", () => {
    if ($("dialogInput").classList.contains("hidden")) {
        finishDialog(true);
    }
});
$("appDialog").addEventListener("cancel", event => {
    event.preventDefault();
    finishDialog(null);
});

$("passwordDialogForm").addEventListener("submit", async event => {
    event.preventDefault();
    const oldPassword = $("currentPasswordInput").value;
    const newPassword = $("newPasswordInput").value;
    const confirmation = $("confirmNewPasswordInput").value;
    const error = $("passwordDialogError");
    if (newPassword !== confirmation) {
        error.textContent = "两次输入的新密码不一致";
        error.classList.remove("hidden");
        return;
    }
    try {
        await api("/api/admin/password", {
            method: "POST",
            body: JSON.stringify({ oldPassword, newPassword })
        });
        $("passwordDialog").close();
        await showAlert("密码修改成功");
    } catch (err) {
        error.textContent = err.message;
        error.classList.remove("hidden");
    }
});
$("passwordDialogCancelBtn").addEventListener("click", () => $("passwordDialog").close());

if (state.token) {
    showDashboard();
    loadAll().catch(() => {
        logout();
    });
} else {
    showLogin();
}
