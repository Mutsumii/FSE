async function verifyFundAccount(accountNo, password) {
  // 通过代理转发到账户系统后端（避免CORS），空字符串 = 相对路径
  const base = API_CONFIG.accountBaseUrl || "";

  const result = await requestJson(
    base,
    API_CONFIG.endpoints.login,
    {
      method: "POST",
      body: { fund_acc_no: accountNo, trade_password: password },
    },
  );
  if (!result.ok)
    return { ok: false, message: result.message || "资金账户系统拒绝登录请求" };
  return normalizeFundAccountLogin(result.data, accountNo);
}

async function completeFundCertificate(subjectType, subjectKey, certificateCode) {
  const base = API_CONFIG.accountBaseUrl || "";

  const result = await requestJson(
    base,
    API_CONFIG.endpoints.completeCertificate,
    {
      method: "POST",
      body: {
        subject_type: subjectType,
        subject_key: subjectKey,
        certificate_code: certificateCode,
      },
    },
  );
  if (!result.ok) {
    return { ok: false, message: result.message || "安全证书认证失败" };
  }
  return normalizeFundAccountLogin(result.data, subjectKey);
}

// 后端返回的 status 可能是英文枚举值（如 "NORMAL"），统一映射为中文
const STATUS_MAP = {
  NORMAL: "正常",
  FROZEN_LOSS: "挂失冻结",
  FROZEN_VIOLATION: "违规冻结",
  FROZEN_NO_FUND: "无资金账户冻结",
  PRE_CLOSE: "预销户",
  CLOSED: "已销户",
};
function normalizeStatus(raw) {
  if (!raw) return "正常";
  return STATUS_MAP[raw] || raw;
}

function normalizeFundAccountLogin(data, accountNo) {
  if (data.success === false || data.ok === false || (data.code !== undefined && data.code !== 0)) {
    return { ok: false, message: data.message || "登录失败" };
  }
  const payload = data.data || data.account || data;
  if (payload.requires_certificate) {
    return {
      ok: true,
      requiresCertificate: true,
      certificateSubjectType: payload.certificate_subject_type || "",
      certificateSubjectKey: payload.certificate_subject_key || accountNo,
      message: "首次登录需要完成安全证书认证",
    };
  }
  const account = {
    accountNo: payload.fund_acc_no || accountNo,
    securityAccountNo: payload.sec_acc_no || accountNo,
    authToken: payload.auth_token || "",
    name: "投资者",
    status: normalizeStatus(payload.status),
    availableCash: Number(payload.available_balance ?? 0),
    frozenCash: Number(payload.frozen_balance ?? 0),
    firstLoginDone: true,
    securityAccountLinked: !!payload.sec_acc_no,
  };
  return { ok: true, requiresCertificate: false, account };
}

function verifyFundAccountMock(accountNo, password) {
  const account = state.accounts[accountNo];
  if (!account) return { ok: false, message: "账户不存在或证券账户未关联" };
  if (account.status === "锁定")
    return { ok: false, message: "账户已锁定，请联系客服" };
  if (account.tradePassword !== password) {
    account.failedAttempts += 1;
    if (account.failedAttempts >= 5) account.status = "锁定";
    saveState();
    return {
      ok: false,
      message:
        account.status === "锁定"
          ? "账户已锁定，请联系客服"
          : "密码错误，请重新输入",
    };
  }
  return {
    ok: true,
    account: {
      accountNo: account.accountNo,
      securityAccountNo: account.securityAccountNo || account.accountNo,
      name: account.name,
      status: account.status,
      availableCash: account.availableCash,
      frozenCash: account.frozenCash,
      firstLoginDone: account.firstLoginDone,
      securityAccountLinked: account.securityAccountLinked !== false,
    },
  };
}

async function fetchFundAccount(accountNo) {
  if (!API_CONFIG.accountBaseUrl) {
    const account = state.accounts[accountNo];
    return {
      ok: true,
      account: {
        accountNo,
        availableCash: account.availableCash,
        frozenCash: account.frozenCash,
        status: account.status,
      },
    };
  }

  const result = await requestJson(
    API_CONFIG.accountBaseUrl,
    API_CONFIG.endpoints.fundAccount,
    { params: { fund_acc_no: accountNo, auth_token: currentAccount()?.authToken } },
  );
  if (!result.ok) return result;
  if (result.data?.code && result.data.code !== 0) {
    return { ok: false, message: result.data.message || "查询资金账户失败" };
  }
  const payload = result.data || result.data.data || result.data.account || result.data;
  return {
    ok: true,
    account: {
      accountNo,
      availableCash: Number(payload.available_balance ?? 0),
      frozenCash: Number(payload.frozen_balance ?? 0),
      status: normalizeStatus(payload.status),
    },
  };
}

async function fetchSecurityHoldings(accountNo) {
  if (!API_CONFIG.accountBaseUrl)
    return { ok: true, holdings: state.accounts[accountNo].holdings };

  const result = await requestJson(
    API_CONFIG.accountBaseUrl,
    API_CONFIG.endpoints.holdings,
    { params: { sec_acc_no: currentAccount()?.securityAccountNo, auth_token: currentAccount()?.authToken } },
  );
  if (!result.ok) return result;
  if (result.data?.code && result.data.code !== 0) {
    return { ok: false, message: result.data.message || "查询证券持仓失败" };
  }
  const payload = result.data.holdings || result.data.data || result.data;
  const rows = Array.isArray(payload) ? payload : [];
  return {
    ok: true,
    holdings: rows.map((item) => ({
      stockCode: item.stock_code,
      quantity: Number(item.quantity ?? 0),
      sellable: Number(item.available_quantity ?? 0),
      cost: Number(item.avg_cost ?? 0),
    })),
  };
}

async function changePasswordViaAccountSystem(
  accountNo,
  type,
  oldPassword,
  newPassword,
) {
  if (!API_CONFIG.accountBaseUrl) return { ok: true };
  return requestJson(
    API_CONFIG.accountBaseUrl,
    API_CONFIG.endpoints.changePassword,
    {
      method: "PUT",
      body: { fund_acc_no: accountNo, auth_token: currentAccount()?.authToken, password_type: type, old_password: oldPassword, new_password: newPassword },
    },
  );
}

async function depositViaAccountSystem(accountNo, amount) {
  return requestJson(API_CONFIG.accountBaseUrl || "", API_CONFIG.endpoints.deposit, {
    method: "POST",
    body: { fund_acc_no: accountNo, auth_token: currentAccount()?.authToken, amount },
  });
}

async function withdrawViaAccountSystem(accountNo, amount, withdrawPassword) {
  return requestJson(API_CONFIG.accountBaseUrl || "", API_CONFIG.endpoints.withdraw, {
    method: "POST",
    body: { fund_acc_no: accountNo, auth_token: currentAccount()?.authToken, amount, withdraw_password: withdrawPassword },
  });
}

// 当前版本冻结/释放已移交中央交易系统，保留空函数供 business.js 本地流程使用
async function freezeFunds(accountNo, amount, orderRef) {
  return { ok: true };
}
async function releaseFunds(accountNo, amount, orderRef) {
  return { ok: true };
}
async function freezeHolding(accountNo, stockCode, quantity, orderRef) {
  return { ok: true };
}
async function releaseHolding(accountNo, stockCode, quantity, orderRef) {
  return { ok: true };
}
