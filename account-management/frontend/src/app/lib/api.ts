const API_BASE = "/api";
const TRADE_MANAGEMENT_API_BASE =
  (import.meta.env.VITE_TRADE_MANAGEMENT_API_BASE as string | undefined) ||
  "http://10.196.95.30:8081/api/trade-management";

const STAFF_TOKEN_KEY = "stock_trading_token";
const STAFF_USERNAME_KEY = "staff_username";
const STAFF_ID_KEY = "staff_id";
const CERTIFICATE_SUBJECT_TYPE_KEY = "certificate_subject_type";
const CERTIFICATE_SUBJECT_KEY_KEY = "certificate_subject_key";
const CERTIFICATE_LOGIN_MODE_KEY = "certificate_login_mode";

type LoginMode = "admin";

export class ApiError extends Error {
  code?: number;
  constructor(message: string, code?: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

class ApiClient {
  private staffToken: string | null = null;

  private readSessionValue(key: string) {
    const sessionValue = window.sessionStorage.getItem(key);
    if (sessionValue !== null) {
      return sessionValue;
    }

    const legacyValue = window.localStorage.getItem(key);
    if (legacyValue !== null) {
      // Migrate existing login state into session storage so refresh keeps it,
      // while closing the tab requires a fresh login.
      window.sessionStorage.setItem(key, legacyValue);
      window.localStorage.removeItem(key);
      return legacyValue;
    }

    return null;
  }

  private writeSessionValue(key: string, value: string) {
    window.sessionStorage.setItem(key, value);
    window.localStorage.removeItem(key);
  }

  private removeSessionValue(key: string) {
    window.sessionStorage.removeItem(key);
    window.localStorage.removeItem(key);
  }

  private getStaffToken() {
    if (this.staffToken) return this.staffToken;
    this.staffToken = this.readSessionValue(STAFF_TOKEN_KEY);
    return this.staffToken;
  }

  setStaffSession(token: string, username?: string | null, staffId?: number | null) {
    this.staffToken = token;
    this.writeSessionValue(STAFF_TOKEN_KEY, token);
    if (username) {
      this.writeSessionValue(STAFF_USERNAME_KEY, username);
    }
    if (staffId !== undefined && staffId !== null) {
      this.writeSessionValue(STAFF_ID_KEY, String(staffId));
    }
  }

  clearStaffSession() {
    this.staffToken = null;
    this.removeSessionValue(STAFF_TOKEN_KEY);
    this.removeSessionValue(STAFF_USERNAME_KEY);
    this.removeSessionValue(STAFF_ID_KEY);
  }

  clearAllSessions() {
    this.clearStaffSession();
    this.removeSessionValue(CERTIFICATE_SUBJECT_TYPE_KEY);
    this.removeSessionValue(CERTIFICATE_SUBJECT_KEY_KEY);
    this.removeSessionValue(CERTIFICATE_LOGIN_MODE_KEY);
  }

  clearToken() {
    this.clearAllSessions();
  }

  getLoginMode(): LoginMode | null {
    if (this.isStaffLoggedIn()) return "admin";
    return null;
  }

  isStaffLoggedIn() {
    return Boolean(this.getStaffToken());
  }

  getStaffUsername() {
    return this.readSessionValue(STAFF_USERNAME_KEY) || "";
  }

  getStaffId() {
    return this.readSessionValue(STAFF_ID_KEY) || "";
  }

  setPendingCertificate(subjectType: string, subjectKey: string, mode: LoginMode) {
    this.writeSessionValue(CERTIFICATE_SUBJECT_TYPE_KEY, subjectType);
    this.writeSessionValue(CERTIFICATE_SUBJECT_KEY_KEY, subjectKey);
    this.writeSessionValue(CERTIFICATE_LOGIN_MODE_KEY, mode);
  }

  getPendingCertificate() {
    const subjectType = this.readSessionValue(CERTIFICATE_SUBJECT_TYPE_KEY);
    const subjectKey = this.readSessionValue(CERTIFICATE_SUBJECT_KEY_KEY);
    const mode = this.readSessionValue(CERTIFICATE_LOGIN_MODE_KEY) as LoginMode | null;
    if (!subjectType || !subjectKey || !mode) {
      return null;
    }
    return { subjectType, subjectKey, mode };
  }

  clearPendingCertificate() {
    this.removeSessionValue(CERTIFICATE_SUBJECT_TYPE_KEY);
    this.removeSessionValue(CERTIFICATE_SUBJECT_KEY_KEY);
    this.removeSessionValue(CERTIFICATE_LOGIN_MODE_KEY);
  }

  private buildHeaders(
    requiresStaffAuth: boolean,
    extraHeaders?: HeadersInit
  ): Record<string, string> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };

    if (requiresStaffAuth) {
      const token = this.getStaffToken();
      if (!token) {
        throw new ApiError("工作人员登录状态已失效，请重新登录", 1018);
      }
      headers["X-Staff-Auth-Token"] = token;
    }

    if (extraHeaders instanceof Headers) {
      extraHeaders.forEach((value, key) => {
        headers[key] = value;
      });
    } else if (Array.isArray(extraHeaders)) {
      extraHeaders.forEach(([key, value]) => {
        headers[key] = value;
      });
    } else if (extraHeaders) {
      Object.assign(headers, extraHeaders);
    }

    return headers;
  }

  private handleUnauthorized(message: string, requiresStaffAuth: boolean) {
    if (requiresStaffAuth) {
      this.clearStaffSession();
      throw new ApiError(message || "工作人员登录状态已失效，请重新登录", 1018);
    }
    throw new ApiError(message || "登录状态已失效，请重新登录", 1018);
  }

  async request<T = any>(
    endpoint: string,
    options: RequestInit = {},
    requiresStaffAuth = false
  ): Promise<T> {
    const url = `${API_BASE}${endpoint}`;
    const response = await fetch(url, {
      ...options,
      headers: this.buildHeaders(requiresStaffAuth, options.headers),
    });

    let data: any = null;
    try {
      data = await response.json();
    } catch {
      data = null;
    }

    if (response.status === 401 || data?.code === 1018) {
      return this.handleUnauthorized(data?.message, requiresStaffAuth);
    }

    if (!response.ok) {
      throw new ApiError(data?.message || `HTTP Error: ${response.status}`, data?.code);
    }

    if (data?.code !== undefined && data.code !== 0) {
      if (data.code === 1018) {
        return this.handleUnauthorized(data.message, requiresStaffAuth);
      }
      throw new ApiError(data.message || "请求失败", data.code);
    }

    return data as T;
  }

  async adminLogin(username: string, password: string) {
    const data = await this.request<any>("/internal/staff/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });

    if (data.auth_token) {
      this.clearPendingCertificate();
      this.setStaffSession(data.auth_token, data.username, data.staff_id);
    }

    if (data.requires_certificate) {
      this.clearAllSessions();
      this.setPendingCertificate(data.certificate_subject_type, data.certificate_subject_key, "admin");
    }

    return data;
  }

  async completeCertificate(certificateCode: string) {
    const pending = this.getPendingCertificate();
    if (!pending) {
      throw new ApiError("没有待完成的首次登录认证");
    }

    const endpoint =
      pending.mode === "admin"
        ? "/internal/staff/complete-certificate"
        : "/external/fund/complete-certificate";

    const data = await this.request<any>(endpoint, {
      method: "POST",
      body: JSON.stringify({
        subject_type: pending.subjectType,
        subject_key: pending.subjectKey,
        certificate_code: certificateCode,
      }),
    });

    if (pending.mode === "admin" && data.auth_token) {
      this.setStaffSession(data.auth_token, data.username, data.staff_id);
      this.clearPendingCertificate();
    }

    return { ...data, mode: pending.mode };
  }

  async listFundAccounts() {
    const response = await this.request<any>("/internal/fund/accounts/list", {}, true);
    return response?.data ?? response;
  }

  async queryFundInfo(fundAccNo: string, idNumber: string, includeLogs = false) {
    return this.request<any>(
      `/internal/fund/accounts?fund_acc_no=${encodeURIComponent(
        fundAccNo
      )}&id_number=${encodeURIComponent(idNumber)}&include_logs=${includeLogs}`,
      {},
      true
    );
  }

  async queryFundLogs(fundAccNo: string, idNumber: string, limit = 50) {
    const response = await this.request<any>(
      `/internal/fund/logs?fund_acc_no=${encodeURIComponent(
        fundAccNo
      )}&id_number=${encodeURIComponent(idNumber)}&limit=${limit}`,
      {},
      true
    );
    return response?.data ?? response;
  }

  async createFundAccount(data: {
    sec_acc_no: string;
    id_number: string;
    currency: string;
    trade_password: string;
    withdraw_password: string;
  }) {
    return this.request<any>(
      "/internal/fund/accounts",
      {
        method: "POST",
        body: JSON.stringify(data),
      },
      true
    );
  }

  async deposit(fundAccNo: string, amount: number) {
    return this.request<any>(
      "/internal/fund/deposit",
      {
        method: "POST",
        body: JSON.stringify({ fund_acc_no: fundAccNo, amount }),
      },
      true
    );
  }

  async withdraw(fundAccNo: string, amount: number, withdrawPassword: string) {
    return this.request<any>(
      "/internal/fund/withdraw",
      {
        method: "POST",
        body: JSON.stringify({
          fund_acc_no: fundAccNo,
          amount,
          withdraw_password: withdrawPassword,
        }),
      },
      true
    );
  }

  async adminChangeFundPassword(
    fundAccNo: string,
    oldPassword: string,
    newPassword: string,
    passwordType: "trade" | "withdraw"
  ) {
    return this.request<any>(
      "/internal/fund/password",
      {
        method: "PUT",
        body: JSON.stringify({
          fund_acc_no: fundAccNo,
          old_password: oldPassword,
          new_password: newPassword,
          password_type: passwordType,
        }),
      },
      true
    );
  }

  async reportFundLoss(fundAccNo: string, secAccNo: string, reason: string, idNumber: string) {
    return this.request<any>(
      "/internal/fund/accounts/loss",
      {
        method: "POST",
        body: JSON.stringify({
          fund_acc_no: fundAccNo,
          sec_acc_no: secAccNo,
          reason,
          id_number: idNumber,
        }),
      },
      true
    );
  }

  async reissueFundAccount(
    oldFundAccNo: string,
    secAccNo: string,
    idNumber: string,
    currency: string,
    newTradePassword: string,
    newWithdrawPassword: string
  ) {
    return this.request<any>(
      "/internal/fund/accounts/reissue",
      {
        method: "POST",
        body: JSON.stringify({
          old_fund_acc_no: oldFundAccNo,
          sec_acc_no: secAccNo,
          id_number: idNumber,
          currency,
          new_trade_password: newTradePassword,
          new_withdraw_password: newWithdrawPassword,
        }),
      },
      true
    );
  }

  async closeFundAccount(fundAccNo: string, reason: string, idNumber: string) {
    return this.request<any>(
      "/internal/fund/accounts/close",
      {
        method: "POST",
        body: JSON.stringify({ fund_acc_no: fundAccNo, reason, id_number: idNumber }),
      },
      true
    );
  }

  async bindSecurityAccount(fundAccNo: string, secAccNo: string) {
    return this.request<any>(
      "/internal/fund/accounts/bind",
      {
        method: "POST",
        body: JSON.stringify({ fund_acc_no: fundAccNo, sec_acc_no: secAccNo }),
      },
      true
    );
  }

  async unbindSecurityAccount(fundAccNo: string, secAccNo: string) {
    return this.request<any>(
      "/internal/fund/accounts/unbind",
      {
        method: "POST",
        body: JSON.stringify({ fund_acc_no: fundAccNo, sec_acc_no: secAccNo }),
      },
      true
    );
  }

  async listSecurityAccounts() {
    const response = await this.request<any>("/internal/security/accounts", {}, true);
    return response?.data ?? response;
  }

  async createSecuritiesAccount(data: Record<string, unknown>) {
    return this.request<any>(
      "/internal/security/accounts",
      {
        method: "POST",
        body: JSON.stringify(data),
      },
      true
    );
  }

  async reportSecurityLoss(secAccNo: string, reason: string, idNumber: string) {
    return this.request<any>(
      "/internal/security/accounts/loss",
      {
        method: "POST",
        body: JSON.stringify({ sec_acc_no: secAccNo, reason, id_number: idNumber }),
      },
      true
    );
  }

  async reissueSecurityAccount(oldSecAccNo: string, openingData: Record<string, unknown>) {
    return this.request<any>(
      "/internal/security/accounts/reissue",
      {
        method: "POST",
        body: JSON.stringify({ old_sec_acc_no: oldSecAccNo, ...openingData }),
      },
      true
    );
  }

  async closeSecurityAccount(secAccNo: string, reason: string, idNumber: string) {
    return this.request<any>(
      "/internal/security/accounts/close",
      {
        method: "POST",
        body: JSON.stringify({ sec_acc_no: secAccNo, reason, id_number: idNumber }),
      },
      true
    );
  }

  async updateInvestorInfo(investorId: number, data: Record<string, unknown>) {
    return this.request<any>(
      "/internal/security/investors",
      {
        method: "PUT",
        body: JSON.stringify({ investor_id: investorId, ...data }),
      },
      true
    );
  }

  async deactivateStaff(targetStaffId: number, reason: string) {
    return this.request<any>(
      "/internal/staff/deactivate",
      {
        method: "POST",
        body: JSON.stringify({ target_staff_id: targetStaffId, reason }),
      },
      true
    );
  }

  async getDashboardStats() {
    const response = await this.request<any>("/internal/dashboard/stats", {}, true);
    return response?.data ?? response;
  }

  async getRecentLogs(limit = 10) {
    const response = await this.request<any>(`/internal/dashboard/recent-logs?limit=${limit}`, {}, true);
    return response?.data ?? response;
  }

  async queryDashboardLogs(params: {
    time_from?: string;
    time_to?: string;
    operation_type?: string;
    account_no?: string;
  }) {
    const query = new URLSearchParams();
    if (params.time_from) query.set("time_from", params.time_from);
    if (params.time_to) query.set("time_to", params.time_to);
    if (params.operation_type) query.set("operation_type", params.operation_type);
    if (params.account_no) query.set("account_no", params.account_no);

    const response = await this.request<any>(
      `/internal/dashboard/logs/query?${query.toString()}`,
      {},
      true
    );
    return response?.data ?? response;
  }

  async checkBlacklist(userName: string): Promise<boolean> {
    const response = await fetch(
      `${TRADE_MANAGEMENT_API_BASE}/blacklist/check?userName=${encodeURIComponent(userName)}`
    );

    if (!response.ok) {
      throw new ApiError(`黑名单查询失败: HTTP ${response.status}`);
    }

    const data = await response.json();
    return Boolean(data?.success && data?.data === true);
  }
}

export const api = new ApiClient();
