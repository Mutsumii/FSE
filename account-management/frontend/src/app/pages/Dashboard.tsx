import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import { Activity, Building, CreditCard, Users } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { api, ApiError } from "../lib/api";

interface Stats {
  security_account_count: number;
  fund_account_count: number;
  today_new_accounts: number;
  abnormal_account_count: number;
}

interface LogEntry {
  log_id: number;
  staff_id: number;
  operation_type: string;
  target_type: string;
  target_id: string;
  security_acc_no?: string | null;
  fund_acc_no?: string | null;
  detail: string;
  operation_time: string;
}

const SECURITY_OVERVIEW_ACTIONS = [
  { label: "\u5f00\u6237", path: "/securities", action: "create" },
  { label: "\u6302\u5931", path: "/securities", action: "loss" },
  { label: "\u8865\u529e", path: "/securities", action: "reissue" },
  { label: "\u9500\u6237", path: "/securities", action: "close" },
];

const FUND_OVERVIEW_ACTIONS = [
  { label: "\u5f00\u6237", path: "/funds", action: "create" },
  { label: "\u6302\u5931", path: "/funds", action: "loss" },
  { label: "\u8865\u529e", path: "/funds", action: "reissue" },
  { label: "\u9500\u6237", path: "/funds", action: "close" },
  { label: "\u6539\u5bc6", path: "/funds", action: "password" },
  { label: "\u5b58\u6b3e", path: "/funds", action: "deposit" },
  { label: "\u53d6\u6b3e", path: "/funds", action: "withdraw" },
];

const OPERATION_TYPE_OPTIONS = [
  { value: "\u8bc1\u5238\u5f00\u6237", label: "\u8bc1\u5238\u5f00\u6237" },
  { value: "\u8d44\u91d1\u5f00\u6237", label: "\u8d44\u91d1\u5f00\u6237" },
  { value: "\u6302\u5931", label: "\u6302\u5931" },
  { value: "\u8865\u529e", label: "\u8865\u529e" },
  { value: "\u9500\u6237", label: "\u9500\u6237" },
  { value: "\u8d44\u91d1\u5b58\u6b3e", label: "\u8d44\u91d1\u5b58\u6b3e" },
  { value: "\u8d44\u91d1\u53d6\u6b3e", label: "\u8d44\u91d1\u53d6\u6b3e" },
  { value: "\u67e5\u8be2\u8d44\u91d1\u8d26\u6237", label: "\u67e5\u8be2\u8d44\u91d1\u8d26\u6237" },
  { value: "\u67e5\u8be2\u8d44\u91d1\u6d41\u6c34", label: "\u67e5\u8be2\u8d44\u91d1\u6d41\u6c34" },
  { value: "\u66f4\u65b0\u6295\u8d44\u8005\u4fe1\u606f", label: "\u66f4\u65b0\u6295\u8d44\u8005\u4fe1\u606f" },
  { value: "\u7ed1\u5b9a\u8bc1\u5238\u8d26\u6237", label: "\u7ed1\u5b9a\u8bc1\u5238\u8d26\u6237" },
  { value: "\u89e3\u7ed1\u8bc1\u5238\u8d26\u6237", label: "\u89e3\u7ed1\u8bc1\u5238\u8d26\u6237" },
  { value: "\u4fee\u6539\u5bc6\u7801", label: "\u4fee\u6539\u5bc6\u7801" },
];

export default function Dashboard() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<Stats | null>(null);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logFilters, setLogFilters] = useState({
    timeFrom: "",
    timeTo: "",
    operationType: "",
    accountNo: "",
  });

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setLoading(true);
        const [statsRes, logsRes] = await Promise.all([api.getDashboardStats(), api.getRecentLogs(10)]);
        setStats(statsRes);
        setLogs(Array.isArray(logsRes) ? logsRes : []);
      } catch (error) {
        console.error("获取 Dashboard 数据失败:", error);
        if (error instanceof ApiError && error.code === 1018) {
          navigate("/login", { replace: true, state: { mode: "admin" } });
        }
      } finally {
        setLoading(false);
      }
    };

    void fetchDashboardData();
  }, [navigate]);

  const handleQueryLogs = async () => {
    try {
      setLogsLoading(true);
      const response = await api.queryDashboardLogs({
        time_from: logFilters.timeFrom ? new Date(logFilters.timeFrom).toISOString() : "",
        time_to: logFilters.timeTo ? new Date(logFilters.timeTo).toISOString() : "",
        operation_type: logFilters.operationType,
        account_no: logFilters.accountNo,
      });
      setLogs(Array.isArray(response?.logs) ? response.logs : []);
    } catch (error) {
      console.error("查询操作日志失败:", error);
      if (error instanceof ApiError && error.code === 1018) {
        navigate("/login", { replace: true, state: { mode: "admin" } });
      }
    } finally {
      setLogsLoading(false);
    }
  };

  const handleOverviewJump = (path: string, action: string) => {
    navigate(`${path}?action=${action}`);
  };

  const handleResetLogs = async () => {
    setLogFilters({
      timeFrom: "",
      timeTo: "",
      operationType: "",
      accountNo: "",
    });

    try {
      setLogsLoading(true);
      const logsRes = await api.getRecentLogs(10);
      setLogs(Array.isArray(logsRes) ? logsRes : []);
    } finally {
      setLogsLoading(false);
    }
  };

  const formatNumber = (num: number | null | undefined) => Number(num ?? 0).toLocaleString("zh-CN");

  const getActionDisplayName = (operationType: string) => {
    const actionMap: Record<string, string> = {
      证券开户: "证券账户开户",
      资金开户: "资金账户开户",
      挂失: "账户挂失",
      补办: "账户补办",
      销户: "账户销户",
      资金存款: "资金存款",
      资金取款: "资金取款",
      查询资金账户: "账户信息查询",
      查询资金流水: "资金流水查询",
      更新投资者信息: "投资者信息更新",
      绑定证券账户: "账户关联绑定",
      解绑证券账户: "账户关联解绑",
      修改密码: "密码修改",
    };
    return actionMap[operationType] || operationType;
  };

  const getLogStatus = (operationType: string) => {
    if (operationType.includes("销户") || operationType.includes("挂失")) return "warning";
    if (operationType.includes("失败") || operationType.includes("拒绝")) return "error";
    return "success";
  };

  const formatTime = (timeStr: string) => {
    const date = new Date(timeStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const hours = Math.floor(diff / (1000 * 60 * 60));

    if (hours < 1) return "刚刚";
    if (hours < 24) return `${hours} 小时前`;
    return date.toLocaleDateString("zh-CN");
  };

  const buildAccountSummary = (log: LogEntry) => {
    const segments = [];
    if (log.security_acc_no) {
      segments.push(`证券账户：${log.security_acc_no}`);
    }
    if (log.fund_acc_no) {
      segments.push(`资金账户：${log.fund_acc_no}`);
    }
    if (segments.length > 0) {
      return segments.join(" | ");
    }
    if (log.target_id) {
      return `目标对象：${log.target_id}`;
    }
    return "";
  };

  const statItems = [
    {
      name: "证券账户总数",
      value: stats ? formatNumber(stats.security_account_count) : "-",
      icon: Users,
      color: "text-red-500",
      bg: "bg-red-100",
    },
    {
      name: "资金账户总数",
      value: stats ? formatNumber(stats.fund_account_count) : "-",
      icon: CreditCard,
      color: "text-red-500",
      bg: "bg-red-100",
    },
    {
      name: "今日新开户",
      value: stats ? formatNumber(stats.today_new_accounts) : "-",
      icon: Building,
      color: "text-red-500",
      bg: "bg-red-100",
    },
    {
      name: "异常账户提醒",
      value: stats ? formatNumber(stats.abnormal_account_count) : "-",
      icon: Activity,
      color: stats && stats.abnormal_account_count > 0 ? "text-orange-500" : "text-green-500",
      bg: stats && stats.abnormal_account_count > 0 ? "bg-orange-100" : "bg-green-100",
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">账户业务总览</h2>
          <p className="text-slate-500">查看证券及资金账户概况与当前工作人员的近期操作。</p>
        </div>
        <div className="flex gap-2">
          <Link
            to="/securities"
            className="inline-flex items-center justify-center rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white shadow hover:bg-red-700"
          >
            证券开户
          </Link>
          <Link
            to="/funds"
            className="inline-flex items-center justify-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50"
          >
            资金开户
          </Link>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {statItems.map((stat) => (
          <Card key={stat.name}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-slate-600">{stat.name}</CardTitle>
              <div className={`${stat.bg} ${stat.color} rounded-full p-2`}>
                <stat.icon className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{loading ? "..." : stat.value}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>证券账户功能总览</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-slate-500">
              开户直接进入开户表单；挂失、补办、销户会先要求输入证券账户号，再进入对应校验和操作页面。
            </p>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {SECURITY_OVERVIEW_ACTIONS.map((item) => (
                <Button
                  key={item.action}
                  variant={item.action === "create" ? "default" : "outline"}
                  className={item.action === "create" ? "bg-red-600 hover:bg-red-700" : ""}
                  onClick={() => handleOverviewJump(item.path, item.action)}
                >
                  {item.label}
                </Button>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>资金账户功能总览</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-slate-500">
              开户直接进入开户表单；其余操作会先要求输入资金账户号，再进入对应校验和操作页面。
            </p>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {FUND_OVERVIEW_ACTIONS.map((item) => (
                <Button
                  key={item.action}
                  variant={item.action === "create" ? "default" : "outline"}
                  className={item.action === "create" ? "bg-red-600 hover:bg-red-700" : ""}
                  onClick={() => handleOverviewJump(item.path, item.action)}
                >
                  {item.label}
                </Button>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>最新操作记录</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div className="space-y-2">
              <Label htmlFor="log-time-from">起始时间</Label>
              <Input
                id="log-time-from"
                type="datetime-local"
                value={logFilters.timeFrom}
                onChange={(e) => setLogFilters((prev) => ({ ...prev, timeFrom: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="log-time-to">结束时间</Label>
              <Input
                id="log-time-to"
                type="datetime-local"
                value={logFilters.timeTo}
                onChange={(e) => setLogFilters((prev) => ({ ...prev, timeTo: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="log-operation-type">操作类型</Label>
              <select
                id="log-operation-type"
                value={logFilters.operationType}
                onChange={(e) => setLogFilters((prev) => ({ ...prev, operationType: e.target.value }))}
                className="flex h-10 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
              >
                <option value="">全部操作类型</option>
                {OPERATION_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="log-account-no">相关账户号</Label>
              <Input
                id="log-account-no"
                placeholder="输入 SA... 或 FA..."
                value={logFilters.accountNo}
                onChange={(e) => setLogFilters((prev) => ({ ...prev, accountNo: e.target.value }))}
              />
            </div>
          </div>
          <div className="mb-4 flex gap-2">
            <Button onClick={handleQueryLogs} disabled={logsLoading}>
              {logsLoading ? "查询中..." : "查询日志"}
            </Button>
            <Button variant="outline" onClick={handleResetLogs} disabled={logsLoading}>
              重置
            </Button>
          </div>
          <div className="space-y-4">
            {loading || logsLoading ? (
              <div className="py-4 text-center text-slate-500">加载中...</div>
            ) : logs.length === 0 ? (
              <div className="py-4 text-center text-slate-500">暂无操作记录</div>
            ) : (
              logs.map((log) => {
                const status = getLogStatus(log.operation_type);
                const accountSummary = buildAccountSummary(log);
                return (
                  <div
                    key={log.log_id}
                    className="flex items-start justify-between border-b border-slate-100 pb-3 last:border-0 last:pb-0"
                  >
                    <div className="space-y-1">
                      <p className="text-sm font-medium text-slate-900">{getActionDisplayName(log.operation_type)}</p>
                      {accountSummary && <p className="text-xs text-slate-600">{accountSummary}</p>}
                      <p className="text-xs text-slate-500">{log.detail || "无附加说明"}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs font-medium text-slate-500">{formatTime(log.operation_time)}</p>
                      <span
                        className={`mt-1 inline-block rounded px-2 py-0.5 text-[10px] ${
                          status === "success"
                            ? "bg-green-100 text-green-700"
                            : status === "warning"
                              ? "bg-orange-100 text-orange-700"
                              : "bg-red-100 text-red-700"
                        }`}
                      >
                        {status === "success" ? "完成" : status === "warning" ? "注意" : "异常"}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
