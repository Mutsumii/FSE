import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import {
  DollarSign,
  Key,
  Plus,
  RefreshCw,
  ScrollText,
  Search,
  ShieldAlert,
  XCircle,
} from "lucide-react";
import { Button } from "../components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "../components/ui/dialog";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
import { api } from "../lib/api";

interface FundAccount {
  fund_acc_no: string;
  sec_acc_no: string;
  name: string;
  id_number: string;
  available_balance: number;
  frozen_balance: number;
  currency: string;
  status: string;
  open_date: string;
}

interface FundLog {
  log_id: number;
  txn_type: string;
  amount?: number | null;
  txn_time?: string | null;
  ref_order_id?: string | null;
  stock_code?: string | null;
  stock_name?: string | null;
  holding_change_type?: string | null;
  share_quantity?: number | null;
  price?: number | null;
  holding_quantity_after?: number | null;
  holding_frozen_quantity_after?: number | null;
}

type AccountAction = "loss" | "reissue" | "close";
type CashAction = "deposit" | "withdraw";
type PasswordType = "trade" | "withdraw";
type FundQuickAction = "create" | "loss" | "reissue" | "close" | "password" | "deposit" | "withdraw";

function maskIdNumber(idNumber: string) {
  if (!idNumber) return "";
  if (idNumber.length <= 8) return idNumber;
  return `${idNumber.slice(0, 3)}********${idNumber.slice(-4)}`;
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined || value === "") return "--";
  return Number(value).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatShareChange(log: FundLog) {
  if (log.share_quantity === null || log.share_quantity === undefined) {
    return "";
  }

  const prefix =
    log.holding_change_type === "INCREASE"
      ? "+"
      : log.holding_change_type === "DECREASE"
        ? "-"
        : "";

  return `${prefix}${log.share_quantity}`;
}

export default function FundAccounts() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [accounts, setAccounts] = useState<FundAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");

  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [createError, setCreateError] = useState("");
  const [createLoading, setCreateLoading] = useState(false);

  const [selectedAccount, setSelectedAccount] = useState<FundAccount | null>(null);
  const [cashAction, setCashAction] = useState<CashAction | null>(null);
  const [isCashModalOpen, setIsCashModalOpen] = useState(false);
  const [cashAmount, setCashAmount] = useState("");
  const [bankCardNo, setBankCardNo] = useState("");
  const [cashPassword, setCashPassword] = useState("");
  const [cashError, setCashError] = useState("");
  const [cashLoading, setCashLoading] = useState(false);

  const [accountAction, setAccountAction] = useState<AccountAction | null>(null);
  const [isAccountActionModalOpen, setIsAccountActionModalOpen] = useState(false);
  const [accountActionReason, setAccountActionReason] = useState("");
  const [accountActionIdNumber, setAccountActionIdNumber] = useState("");
  const [accountActionSecAccNo, setAccountActionSecAccNo] = useState("");
  const [reissueCurrency, setReissueCurrency] = useState("CNY");
  const [reissueTradePassword, setReissueTradePassword] = useState("");
  const [reissueWithdrawPassword, setReissueWithdrawPassword] = useState("");
  const [accountActionError, setAccountActionError] = useState("");
  const [accountActionLoading, setAccountActionLoading] = useState(false);

  const [isPasswordModalOpen, setIsPasswordModalOpen] = useState(false);
  const [passwordType, setPasswordType] = useState<PasswordType>("trade");
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [passwordLoading, setPasswordLoading] = useState(false);

  const [isLogsModalOpen, setIsLogsModalOpen] = useState(false);
  const [logsAccount, setLogsAccount] = useState<FundAccount | null>(null);
  const [logs, setLogs] = useState<FundLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logsError, setLogsError] = useState("");
  const [quickAction, setQuickAction] = useState<FundQuickAction | null>(null);
  const [quickActionAccountNo, setQuickActionAccountNo] = useState("");
  const [quickActionError, setQuickActionError] = useState("");
  const [quickActionModalOpen, setQuickActionModalOpen] = useState(false);

  useEffect(() => {
    void fetchAccounts();
  }, []);

  useEffect(() => {
    if (loading) {
      return;
    }

    const action = searchParams.get("action") as FundQuickAction | null;
    if (!action) {
      return;
    }

    if (action === "create") {
      setCreateError("");
      setIsCreateModalOpen(true);
      setSearchParams({}, { replace: true });
      return;
    }

    setQuickAction(action);
    setQuickActionAccountNo("");
    setQuickActionError("");
    setQuickActionModalOpen(true);
    setSearchParams({}, { replace: true });
  }, [loading, searchParams, setSearchParams]);

  const fetchAccounts = async () => {
    try {
      setLoading(true);
      const response = await api.listFundAccounts();
      setAccounts(Array.isArray(response) ? response : []);
    } catch (error) {
      console.error("获取资金账户列表失败:", error);
    } finally {
      setLoading(false);
    }
  };

  const visibleAccounts = useMemo(
    () => accounts.filter((account) => account.status !== "closed"),
    [accounts]
  );

  const normalAccounts = useMemo(
    () => visibleAccounts.filter((account) => account.status === "normal"),
    [visibleAccounts]
  );

  const frozenAccounts = useMemo(
    () => visibleAccounts.filter((account) => account.status !== "normal"),
    [visibleAccounts]
  );

  const filterByKeyword = (list: FundAccount[]) => {
    const keyword = searchTerm.trim().toLowerCase();
    if (!keyword) return list;

    return list.filter((account) =>
      [account.name, account.fund_acc_no, account.sec_acc_no, account.currency]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(keyword))
    );
  };

  const filteredNormalAccounts = useMemo(
    () => filterByKeyword(normalAccounts),
    [normalAccounts, searchTerm]
  );
  const filteredFrozenAccounts = useMemo(
    () => filterByKeyword(frozenAccounts),
    [frozenAccounts, searchTerm]
  );

  const resetCreateForm = () => {
    setCreateError("");
    const form = document.getElementById("fund-account-form") as HTMLFormElement | null;
    form?.reset();
  };

  const handleCreateFundAccount = async () => {
    const form = document.getElementById("fund-account-form") as HTMLFormElement | null;
    if (!form) return;

    const formData = new FormData(form);
    const payload = {
      sec_acc_no: String(formData.get("sec_acc_no") || "").trim(),
      id_number: String(formData.get("id_number") || "").trim(),
      currency: String(formData.get("currency") || "CNY").trim(),
      trade_password: String(formData.get("trade_password") || "").trim(),
      withdraw_password: String(formData.get("withdraw_password") || "").trim(),
    };

    if (!payload.sec_acc_no || !payload.id_number || !payload.trade_password || !payload.withdraw_password) {
      setCreateError("请填写完整的开户信息");
      return;
    }
    if (payload.trade_password.length !== 6 || payload.withdraw_password.length !== 6) {
      setCreateError("密码必须为 6 位数字");
      return;
    }

    setCreateLoading(true);
    setCreateError("");
    try {
      const result = await api.createFundAccount(payload);
      window.alert(`资金账户开户成功，资金账号：${result.fund_acc_no || "已生成"}`);
      setIsCreateModalOpen(false);
      resetCreateForm();
      await fetchAccounts();
    } catch (error: any) {
      setCreateError(error.message || "开户失败");
    } finally {
      setCreateLoading(false);
    }
  };

  const openCashModal = (account: FundAccount, action: CashAction) => {
    setSelectedAccount(account);
    setCashAction(action);
    setCashAmount("");
    setBankCardNo("");
    setCashPassword("");
    setCashError("");
    setIsCashModalOpen(true);
  };

  const handleCashSubmit = async () => {
    if (!selectedAccount || !cashAction) return;

    const amount = Number(cashAmount);
    if (!Number.isFinite(amount) || amount <= 0) {
      setCashError("请输入有效金额");
      return;
    }

    if (cashAction === "withdraw") {
      if (cashPassword.length !== 6) {
        setCashError("请输入 6 位取款密码");
        return;
      }
      if (amount > Number(selectedAccount.available_balance || 0)) {
        setCashError("取款金额不能超过当前可用余额");
        return;
      }
    }

    setCashLoading(true);
    setCashError("");
    try {
      if (cashAction === "deposit") {
        await api.deposit(selectedAccount.fund_acc_no, amount);
      } else {
        await api.withdraw(selectedAccount.fund_acc_no, amount, cashPassword);
      }
      setIsCashModalOpen(false);
      await fetchAccounts();
    } catch (error: any) {
      setCashError(error.message || "操作失败");
    } finally {
      setCashLoading(false);
    }
  };

  const openAccountActionModal = (account: FundAccount, action: AccountAction) => {
    setSelectedAccount(account);
    setAccountAction(action);
    setAccountActionReason("");
    setAccountActionIdNumber("");
    setAccountActionSecAccNo("");
    setReissueCurrency(account.currency || "CNY");
    setReissueTradePassword("");
    setReissueWithdrawPassword("");
    setAccountActionError("");
    setIsAccountActionModalOpen(true);
  };

  const handleAccountActionSubmit = async () => {
    if (!selectedAccount || !accountAction) return;

    if (!accountActionIdNumber.trim()) {
      setAccountActionError("请由工作人员手动输入身份证或法人注册登记号");
      return;
    }
    if ((accountAction === "loss" || accountAction === "reissue") && !accountActionSecAccNo.trim()) {
      setAccountActionError("请手动输入并核对证券账户号");
      return;
    }

    if (accountAction === "reissue") {
      if (reissueTradePassword.length !== 6 || reissueWithdrawPassword.length !== 6) {
        setAccountActionError("补办时请输入新的 6 位交易密码和取款密码");
        return;
      }
    }

    setAccountActionLoading(true);
    setAccountActionError("");
    try {
      if (accountAction === "loss") {
        await api.reportFundLoss(
          selectedAccount.fund_acc_no,
          accountActionSecAccNo.trim(),
          accountActionReason,
          accountActionIdNumber.trim()
        );
      } else if (accountAction === "reissue") {
        await api.reissueFundAccount(
          selectedAccount.fund_acc_no,
          accountActionSecAccNo.trim(),
          accountActionIdNumber.trim(),
          reissueCurrency,
          reissueTradePassword,
          reissueWithdrawPassword
        );
      } else {
        await api.closeFundAccount(
          selectedAccount.fund_acc_no,
          accountActionReason,
          accountActionIdNumber.trim()
        );
      }

      setIsAccountActionModalOpen(false);
      await fetchAccounts();
    } catch (error: any) {
      setAccountActionError(error.message || "操作失败");
    } finally {
      setAccountActionLoading(false);
    }
  };

  const openPasswordModal = (account: FundAccount) => {
    setSelectedAccount(account);
    setPasswordType("trade");
    setOldPassword("");
    setNewPassword("");
    setPasswordError("");
    setIsPasswordModalOpen(true);
  };

  const handleQuickActionSubmit = () => {
    if (!quickAction) return;

    const accountNo = quickActionAccountNo.trim();
    if (!accountNo) {
      setQuickActionError("请输入资金账户号");
      return;
    }

    const account = accounts.find((item) => item.fund_acc_no === accountNo);
    if (!account) {
      setQuickActionError("未找到对应的资金账户");
      return;
    }

    if (quickAction === "deposit" || quickAction === "withdraw" || quickAction === "password") {
      if (account.status !== "normal") {
        setQuickActionError("该资金账户当前不是正常状态，无法执行该操作");
        return;
      }
      setQuickActionModalOpen(false);
      if (quickAction === "password") {
        openPasswordModal(account);
      } else {
        openCashModal(account, quickAction);
      }
      return;
    }

    if (quickAction === "loss" || quickAction === "close") {
      if (account.status !== "normal") {
        setQuickActionError("该资金账户当前不是正常状态，无法执行该操作");
        return;
      }
      setQuickActionModalOpen(false);
      openAccountActionModal(account, quickAction);
      return;
    }

    if (quickAction === "reissue") {
      if (account.status === "normal") {
        setQuickActionError("只有冻结状态的资金账户才能补办");
        return;
      }
      setQuickActionModalOpen(false);
      openAccountActionModal(account, "reissue");
    }
  };

  const getQuickActionTitle = () => {
    switch (quickAction) {
      case "loss":
        return "资金账户挂失";
      case "reissue":
        return "资金账户补办";
      case "close":
        return "资金账户销户";
      case "password":
        return "资金账户改密";
      case "deposit":
        return "资金账户存款";
      case "withdraw":
        return "资金账户取款";
      default:
        return "资金账户操作";
    }
  };

  const handlePasswordSubmit = async () => {
    if (!selectedAccount) return;

    if (oldPassword.length !== 6 || newPassword.length !== 6) {
      setPasswordError("请输入 6 位旧密码和新密码");
      return;
    }
    if (oldPassword === newPassword) {
      setPasswordError("新密码不能与旧密码相同");
      return;
    }

    setPasswordLoading(true);
    setPasswordError("");
    try {
      await api.adminChangeFundPassword(selectedAccount.fund_acc_no, oldPassword, newPassword, passwordType);
      setIsPasswordModalOpen(false);
    } catch (error: any) {
      setPasswordError(error.message || "修改密码失败");
    } finally {
      setPasswordLoading(false);
    }
  };

  const openLogsModal = async (account: FundAccount) => {
    setLogsAccount(account);
    setLogs([]);
    setLogsError("");
    setIsLogsModalOpen(true);
    setLogsLoading(true);

    try {
      const response = await api.queryFundLogs(account.fund_acc_no, account.id_number, 50);
      setLogs(Array.isArray(response) ? response : []);
    } catch (error: any) {
      setLogsError(error.message || "查询资金流水失败");
    } finally {
      setLogsLoading(false);
    }
  };

  const renderRows = (list: FundAccount[], emptyText: string) => {
    if (loading) {
      return (
        <TableRow>
          <TableCell colSpan={9} className="py-8 text-center text-slate-500">
            加载中...
          </TableCell>
        </TableRow>
      );
    }

    if (list.length === 0) {
      return (
        <TableRow>
          <TableCell colSpan={9} className="py-8 text-center text-slate-500">
            {emptyText}
          </TableCell>
        </TableRow>
      );
    }

    return list.map((account) => (
      <TableRow key={account.fund_acc_no} data-testid={`fund-row-${account.fund_acc_no}`}>
        <TableCell className="font-medium">{account.fund_acc_no}</TableCell>
        <TableCell>{account.sec_acc_no || "未绑定"}</TableCell>
        <TableCell>{account.name || "未知"}</TableCell>
        <TableCell className="text-slate-500">{maskIdNumber(account.id_number)}</TableCell>
        <TableCell className="text-right font-mono">{formatMoney(account.available_balance)}</TableCell>
        <TableCell className="text-right font-mono">{formatMoney(account.frozen_balance)}</TableCell>
        <TableCell>{account.currency}</TableCell>
        <TableCell>{account.open_date}</TableCell>
        <TableCell className="text-right">
          <div className="flex justify-end gap-2">
            {account.status === "normal" && (
              <>
                <Button
                  data-testid={`fund-deposit-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-green-200 text-green-700 hover:bg-green-50"
                  onClick={() => openCashModal(account, "deposit")}
                >
                  <Plus className="mr-1 h-3 w-3" />
                  存款
                </Button>
                <Button
                  data-testid={`fund-withdraw-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-orange-200 text-orange-700 hover:bg-orange-50"
                  onClick={() => openCashModal(account, "withdraw")}
                >
                  <DollarSign className="mr-1 h-3 w-3" />
                  取款
                </Button>
                <Button
                  data-testid={`fund-logs-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-slate-200 text-slate-700 hover:bg-slate-50"
                  onClick={() => openLogsModal(account)}
                >
                  <ScrollText className="mr-1 h-3 w-3" />
                  流水
                </Button>
                <Button
                  data-testid={`fund-password-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-slate-200 text-slate-700 hover:bg-slate-50"
                  onClick={() => openPasswordModal(account)}
                >
                  <Key className="mr-1 h-3 w-3" />
                  改密
                </Button>
                <Button
                  data-testid={`fund-loss-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-yellow-200 text-yellow-700 hover:bg-yellow-50"
                  onClick={() => openAccountActionModal(account, "loss")}
                >
                  <ShieldAlert className="mr-1 h-3 w-3" />
                  挂失
                </Button>
                <Button
                  data-testid={`fund-close-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-red-200 text-red-600 hover:bg-red-50"
                  onClick={() => openAccountActionModal(account, "close")}
                >
                  <XCircle className="mr-1 h-3 w-3" />
                  销户
                </Button>
              </>
            )}

            {account.status !== "normal" && (
              <>
                <Button
                  data-testid={`fund-frozen-logs-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-slate-200 text-slate-700 hover:bg-slate-50"
                  onClick={() => openLogsModal(account)}
                >
                  <ScrollText className="mr-1 h-3 w-3" />
                  流水
                </Button>
                <Button
                  data-testid={`fund-reissue-${account.fund_acc_no}`}
                  variant="outline"
                  size="sm"
                  className="h-8 border-red-200 text-red-600 hover:bg-red-50"
                  onClick={() => openAccountActionModal(account, "reissue")}
                >
                  <RefreshCw className="mr-1 h-3 w-3" />
                  补办
                </Button>
              </>
            )}
          </div>
        </TableCell>
      </TableRow>
    ));
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">资金账户业务</h2>
          <p className="text-slate-500">管理资金账户开户、出入金、挂失、补办和销户。</p>
        </div>
        <Dialog
          open={isCreateModalOpen}
          onOpenChange={(open) => {
            setIsCreateModalOpen(open);
            if (!open) {
              resetCreateForm();
            }
          }}
        >
          <DialogTrigger asChild>
            <Button data-testid="open-fund-create" className="bg-red-600 hover:bg-red-700">
              <Plus className="mr-2 h-4 w-4" />
              开设资金账户
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>开设资金账户</DialogTitle>
              <DialogDescription>根据已存在的证券账户开立资金账户，并自动完成绑定。</DialogDescription>
            </DialogHeader>
            <form id="fund-account-form" className="grid grid-cols-2 gap-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="fund-sec-acc-no">证券账户号</Label>
                <Input id="fund-sec-acc-no" name="sec_acc_no" placeholder="请输入证券账户号" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="fund-id-number">身份证号</Label>
                <Input id="fund-id-number" name="id_number" placeholder="请输入身份证号" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="fund-currency">币种</Label>
                <select
                  id="fund-currency"
                  name="currency"
                  className="flex h-10 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                  defaultValue="CNY"
                >
                  <option value="CNY">人民币 (CNY)</option>
                  <option value="USD">美元 (USD)</option>
                  <option value="HKD">港币 (HKD)</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="fund-trade-password">交易密码</Label>
                <Input
                  id="fund-trade-password"
                  name="trade_password"
                  type="password"
                  maxLength={6}
                  placeholder="请输入 6 位交易密码"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="fund-withdraw-password">取款密码</Label>
                <Input
                  id="fund-withdraw-password"
                  name="withdraw_password"
                  type="password"
                  maxLength={6}
                  placeholder="请输入 6 位取款密码"
                />
              </div>
            </form>
            {createError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">{createError}</div>
            )}
            <DialogFooter>
              <Button variant="outline" onClick={() => setIsCreateModalOpen(false)}>
                取消
              </Button>
              <Button data-testid="fund-create-submit" onClick={handleCreateFundAccount} disabled={createLoading}>
                {createLoading ? "提交中..." : "确认开户"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      <div className="flex max-w-sm items-center gap-2">
        <div className="relative w-full">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-500" />
          <Input
            className="pl-9"
            placeholder="搜索资金账号 / 姓名 / 证券账号 / 币种..."
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
          />
        </div>
        <Button variant="outline" size="sm" onClick={fetchAccounts} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      <div className="space-y-4">
        <div>
          <h3 className="mb-2 text-lg font-semibold">正常账户</h3>
          <div className="rounded-md border border-slate-200 bg-white">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>资金账号</TableHead>
                  <TableHead>证券账号</TableHead>
                  <TableHead>姓名</TableHead>
                  <TableHead>证件号</TableHead>
                  <TableHead className="text-right">可用余额</TableHead>
                  <TableHead className="text-right">冻结余额</TableHead>
                  <TableHead>币种</TableHead>
                  <TableHead>开户日期</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>{renderRows(filteredNormalAccounts, "暂无正常账户")}</TableBody>
            </Table>
          </div>
        </div>

        <div>
          <h3 className="mb-2 text-lg font-semibold">冻结账户</h3>
          <div className="rounded-md border border-slate-200 bg-white">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>资金账号</TableHead>
                  <TableHead>证券账号</TableHead>
                  <TableHead>姓名</TableHead>
                  <TableHead>证件号</TableHead>
                  <TableHead className="text-right">可用余额</TableHead>
                  <TableHead className="text-right">冻结余额</TableHead>
                  <TableHead>币种</TableHead>
                  <TableHead>开户日期</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>{renderRows(filteredFrozenAccounts, "暂无冻结账户")}</TableBody>
            </Table>
          </div>
        </div>
      </div>

      <Dialog open={isCashModalOpen} onOpenChange={setIsCashModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{cashAction === "deposit" ? "资金账户存款" : "资金账户取款"}</DialogTitle>
            <DialogDescription>账户：{selectedAccount?.fund_acc_no ?? "--"}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>金额</Label>
              <Input type="number" value={cashAmount} onChange={(event) => setCashAmount(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>银行卡号</Label>
              <Input
                value={bankCardNo}
                onChange={(event) => setBankCardNo(event.target.value)}
                placeholder="请输入银行卡号"
              />
            </div>
            {cashAction === "withdraw" && (
              <div className="space-y-2">
                <Label>取款密码</Label>
                <Input
                  type="password"
                  value={cashPassword}
                  onChange={(event) => setCashPassword(event.target.value)}
                  maxLength={6}
                />
              </div>
            )}
            {cashError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">{cashError}</div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCashModalOpen(false)}>
              取消
            </Button>
            <Button data-testid="fund-cash-submit" onClick={handleCashSubmit} disabled={cashLoading}>
              {cashLoading ? "处理中..." : "确认提交"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isPasswordModalOpen} onOpenChange={setIsPasswordModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>修改资金账户密码</DialogTitle>
            <DialogDescription>账户：{selectedAccount?.fund_acc_no ?? "--"}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>密码类型</Label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant={passwordType === "trade" ? "default" : "outline"}
                  onClick={() => setPasswordType("trade")}
                  className={passwordType === "trade" ? "bg-red-600" : ""}
                >
                  交易密码
                </Button>
                <Button
                  type="button"
                  variant={passwordType === "withdraw" ? "default" : "outline"}
                  onClick={() => setPasswordType("withdraw")}
                  className={passwordType === "withdraw" ? "bg-red-600" : ""}
                >
                  取款密码
                </Button>
              </div>
            </div>
            <div className="space-y-2">
              <Label>旧密码</Label>
              <Input
                type="password"
                value={oldPassword}
                onChange={(event) => setOldPassword(event.target.value)}
                maxLength={6}
              />
            </div>
            <div className="space-y-2">
              <Label>新密码</Label>
              <Input
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                maxLength={6}
              />
            </div>
            {passwordError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">
                {passwordError}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsPasswordModalOpen(false)}>
              取消
            </Button>
            <Button data-testid="fund-password-submit" onClick={handlePasswordSubmit} disabled={passwordLoading}>
              {passwordLoading ? "处理中..." : "确认修改"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={isLogsModalOpen}
        onOpenChange={(open) => {
          setIsLogsModalOpen(open);
          if (!open) {
            setLogsAccount(null);
            setLogs([]);
            setLogsError("");
            setLogsLoading(false);
          }
        }}
      >
        <DialogContent className="max-w-6xl">
          <DialogHeader>
            <DialogTitle>资金流水查询</DialogTitle>
            <DialogDescription>
              账户：{logsAccount?.fund_acc_no ?? "--"}，默认展示最近 50 条资金流水记录。
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-[60vh] overflow-auto rounded-md border border-slate-200">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>时间</TableHead>
                  <TableHead>类型</TableHead>
                  <TableHead className="text-right">金额</TableHead>
                  <TableHead>关联单号</TableHead>
                  <TableHead>股票名称</TableHead>
                  <TableHead>股票代码</TableHead>
                  <TableHead className="text-right">股数变化</TableHead>
                  <TableHead className="text-right">成交价格</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {logsLoading && (
                  <TableRow>
                    <TableCell colSpan={8} className="py-8 text-center text-slate-500">
                      正在加载资金流水...
                    </TableCell>
                  </TableRow>
                )}
                {!logsLoading && logsError && (
                  <TableRow>
                    <TableCell colSpan={8} className="py-8 text-center text-red-600">
                      {logsError}
                    </TableCell>
                  </TableRow>
                )}
                {!logsLoading && !logsError && logs.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8} className="py-8 text-center text-slate-500">
                      暂无资金流水
                    </TableCell>
                  </TableRow>
                )}
                {!logsLoading &&
                  !logsError &&
                  logs.map((log) => (
                    <TableRow key={log.log_id}>
                      <TableCell>{log.txn_time || "--"}</TableCell>
                      <TableCell>{log.txn_type || "--"}</TableCell>
                      <TableCell className="text-right font-mono">{formatMoney(log.amount)}</TableCell>
                      <TableCell>{log.ref_order_id || "--"}</TableCell>
                      <TableCell>{log.stock_name || ""}</TableCell>
                      <TableCell className="font-mono">{log.stock_code || ""}</TableCell>
                      <TableCell className="text-right font-mono">{formatShareChange(log)}</TableCell>
                      <TableCell className="text-right font-mono">
                        {log.price === null || log.price === undefined ? "" : formatMoney(log.price)}
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsLogsModalOpen(false)}>
              关闭
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isAccountActionModalOpen} onOpenChange={setIsAccountActionModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {accountAction === "loss"
                ? "资金账户挂失"
                : accountAction === "reissue"
                  ? "资金账户补办"
                  : "资金账户销户"}
            </DialogTitle>
            <DialogDescription>
              {accountAction === "reissue"
                ? "按开户手续重新办理。原账户的全部资金将迁移到新账户，资金和证券账户将重新激活。"
                : "挂失时必须同时核对身份证/法人注册登记号与证券账户号。"}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>身份证号 / 法人注册登记号</Label>
              <Input
                value={accountActionIdNumber}
                onChange={(event) => setAccountActionIdNumber(event.target.value)}
                placeholder="请输入身份证号"
              />
            </div>
            {(accountAction === "loss" || accountAction === "reissue") && (
              <div className="space-y-2">
                <Label>证券账户号</Label>
                <Input
                  value={accountActionSecAccNo}
                  onChange={(event) => setAccountActionSecAccNo(event.target.value)}
                  placeholder="请手动输入证券账户号"
                />
              </div>
            )}
            {(accountAction === "loss" || accountAction === "close") && (
              <div className="space-y-2">
                <Label>操作原因</Label>
                <Input
                  value={accountActionReason}
                  onChange={(event) => setAccountActionReason(event.target.value)}
                  placeholder="请输入操作原因"
                />
              </div>
            )}
            {accountAction === "reissue" && (
              <>
                <div className="space-y-2">
                  <Label>币种</Label>
                  <select
                    value={reissueCurrency}
                    onChange={(event) => setReissueCurrency(event.target.value)}
                    className="flex h-10 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
                  >
                    <option value="CNY">人民币 (CNY)</option>
                    <option value="USD">美元 (USD)</option>
                    <option value="HKD">港币 (HKD)</option>
                  </select>
                </div>
                <div className="space-y-2">
                  <Label>新交易密码</Label>
                  <Input
                    type="password"
                    value={reissueTradePassword}
                    onChange={(event) => setReissueTradePassword(event.target.value)}
                    maxLength={6}
                  />
                </div>
                <div className="space-y-2">
                  <Label>新取款密码</Label>
                  <Input
                    type="password"
                    value={reissueWithdrawPassword}
                    onChange={(event) => setReissueWithdrawPassword(event.target.value)}
                    maxLength={6}
                  />
                </div>
              </>
            )}
            {accountActionError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">
                {accountActionError}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAccountActionModalOpen(false)}>
              取消
            </Button>
            <Button data-testid="fund-account-action-submit" onClick={handleAccountActionSubmit} disabled={accountActionLoading}>
              {accountActionLoading ? "处理中..." : "确认提交"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={quickActionModalOpen}
        onOpenChange={(open) => {
          setQuickActionModalOpen(open);
          if (!open) {
            setQuickAction(null);
            setQuickActionAccountNo("");
            setQuickActionError("");
          }
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{getQuickActionTitle()}</DialogTitle>
            <DialogDescription>请先输入资金账户号，再进入对应的校验和操作流程。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="fund-quick-action-account">资金账户号</Label>
              <Input
                id="fund-quick-action-account"
                value={quickActionAccountNo}
                onChange={(e) => setQuickActionAccountNo(e.target.value)}
                placeholder="请输入 FA..."
              />
            </div>
            {quickActionError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">
                {quickActionError}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setQuickActionModalOpen(false)}>
              取消
            </Button>
            <Button onClick={handleQuickActionSubmit}>继续</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
