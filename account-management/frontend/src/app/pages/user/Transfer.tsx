import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card";
import { Button } from "../../components/ui/button";
import { Wallet, ArrowRightLeft, RefreshCw } from "lucide-react";
import { api } from "../../lib/api";

export default function Transfer() {
  const [account, setAccount] = useState<any>(null);
  const [direction, setDirection] = useState<"bank_to_securities" | "securities_to_bank">("bank_to_securities");
  const [amount, setAmount] = useState("");
  const [withdrawPassword, setWithdrawPassword] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState<"success" | "error">("success");

  useEffect(() => {
    loadAccount();
  }, []);

  const loadAccount = async () => {
    setLoading(true);
    try {
      const data = await api.getMyAccount();
      setAccount(data);
    } catch (err: any) {
      showMessage(err.message || "加载账户失败", "error");
    } finally {
      setLoading(false);
    }
  };

  const showMessage = (msg: string, type: "success" | "error") => {
    setMessage(msg);
    setMessageType(type);
    setTimeout(() => setMessage(""), 5000);
  };

  const handleTransfer = async () => {
    if (!amount || !withdrawPassword) {
      showMessage("请填写完整的转账信息", "error");
      return;
    }

    const numAmount = Number(amount);
    if (numAmount <= 0) {
      showMessage("转账金额必须大于0", "error");
      return;
    }

    // 证转银时检查余额
    if (direction === "securities_to_bank") {
      const availableBalance = account?.availableBalance || account?.balance || 0;
      if (numAmount > availableBalance) {
        showMessage("转出金额不能超过可用余额", "error");
        return;
      }
    }

    setSubmitting(true);
    try {
      // 银转证调用存款API，证转银调用取款API
      let result;
      if (direction === "bank_to_securities") {
        // 银转证 - 需要资金账号和身份证号
        const fundAccNo = localStorage.getItem('fund_acc_no') || '';
        result = await api.deposit(fundAccNo, numAmount, '');
      } else {
        // 证转银 - 需要资金账号、金额、身份证号、取款密码
        const fundAccNo = localStorage.getItem('fund_acc_no') || '';
        const idNumber = localStorage.getItem('id_number') || '';
        result = await api.withdraw(fundAccNo, numAmount, idNumber, withdrawPassword);
      }
      
      if (result.code === 0) {
        const balanceAfter = result.available_balance || result.data?.available_balance || 0;
        showMessage(
          `转账成功！${direction === "bank_to_securities" ? "转入" : "转出"} ${numAmount.toLocaleString()} 元`,
          "success"
        );
        setAmount("");
        setWithdrawPassword("");
        loadAccount();
      } else {
        showMessage(result.message || "转账失败", "error");
      }
    } catch (err: any) {
      showMessage(err.message || "转账失败", "error");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-6 max-w-6xl mx-auto">
        <h2 className="text-2xl font-bold">银证转账</h2>
        <div className="flex items-center justify-center h-64">
          <RefreshCw className="w-6 h-6 animate-spin text-red-600" />
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-6xl mx-auto">
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl font-bold tracking-tight text-slate-900">银证转账</h2>
        <p className="text-slate-500">银行账户与证券资金账户之间资金划转</p>
      </div>

      {message && (
        <div className={`p-4 rounded-md ${messageType === "success" ? "bg-green-50 border border-green-200 text-green-700" : "bg-red-50 border border-red-200 text-red-600"}`}>
          {message}
        </div>
      )}

      <div className="grid gap-6 md:grid-cols-2">
        {/* Account Info */}
        <Card className="shadow-sm border-slate-200">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Wallet className="w-5 h-5 text-red-600" />
              <span>账户信息</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              <div className="flex justify-between py-2 border-b">
                <span className="text-slate-500">资金账号</span>
                <span className="font-mono font-medium">{account?.accountNo}</span>
              </div>
              <div className="flex justify-between py-2 border-b">
                <span className="text-slate-500">持有人</span>
                <span>{account?.name}</span>
              </div>
              <div className="flex justify-between py-2 border-b">
                <span className="text-slate-500">可用资金</span>
                <span className="font-mono font-semibold text-red-600">
                  {(account?.availableBalance || account?.balance || 0).toLocaleString("zh-CN", { minimumFractionDigits: 2 })}
                </span>
              </div>
              <div className="flex justify-between py-2">
                <span className="text-slate-500">冻结资金</span>
                <span className="font-mono text-slate-500">
                  {(account?.frozenBalance || account?.frozenAmount || 0).toLocaleString("zh-CN", { minimumFractionDigits: 2 })}
                </span>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Transfer Form */}
        <Card className="shadow-sm border-slate-200">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ArrowRightLeft className="w-5 h-5 text-red-600" />
              <span>转账操作</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex gap-3 mb-4">
                <Button
                  className={`flex-1 ${direction === "bank_to_securities" ? "bg-blue-600 text-white" : "bg-slate-100 text-slate-600"}`}
                  onClick={() => setDirection("bank_to_securities")}
                >
                  银转证
                </Button>
                <Button
                  className={`flex-1 ${direction === "securities_to_bank" ? "bg-blue-600 text-white" : "bg-slate-100 text-slate-600"}`}
                  onClick={() => setDirection("securities_to_bank")}
                >
                  证转银
                </Button>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  {direction === "bank_to_securities" ? "转入金额" : "转出金额"}
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500"
                  placeholder="请输入金额"
                />
                {direction === "securities_to_bank" && account && (
                  <p className="text-xs text-slate-400 mt-1">
                    最大可转出: {(account.availableBalance || account.balance || 0).toLocaleString("zh-CN", { minimumFractionDigits: 2 })} 元
                  </p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">取款密码</label>
                <input
                  type="password"
                  value={withdrawPassword}
                  onChange={(e) => setWithdrawPassword(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500"
                  placeholder="请输入取款密码"
                />
              </div>

              <Button
                className="w-full bg-red-600 hover:bg-red-700 text-white"
                onClick={handleTransfer}
                disabled={submitting}
              >
                {submitting ? "处理中..." : "确认转账"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
