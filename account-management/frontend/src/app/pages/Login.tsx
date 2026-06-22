import { useEffect, useState } from "react";
import { ArrowLeft, Briefcase } from "lucide-react";
import { useLocation, useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { api } from "../lib/api";

type LocationState = {
  from?: string;
};

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = (location.state as LocationState | null) ?? null;

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (api.getPendingCertificate()) {
      navigate("/certificate", { replace: true });
      return;
    }

    if (api.isStaffLoggedIn()) {
      navigate("/", { replace: true });
    }
  }, [navigate]);

  const handleLogin = async () => {
    const trimmedUsername = username.trim();
    const trimmedPassword = password.trim();

    if (!trimmedUsername || !trimmedPassword) {
      setError("请输入完整的工作人员账号和密码");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const result = await api.adminLogin(trimmedUsername, trimmedPassword);
      if (result.requires_certificate) {
        navigate("/certificate", { replace: true });
        return;
      }
      if (!result.auth_token) {
        throw new Error("登录失败，未获取到认证令牌");
      }
      const target = locationState?.from && locationState.from !== "/login" ? locationState.from : "/";
      navigate(target, { replace: true });
    } catch (err: any) {
      setError(err.message || "登录失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="grid min-h-screen place-items-center bg-slate-100 px-4 py-10">
      <div className="relative grid w-full max-w-5xl overflow-hidden rounded-xl border border-white/40 bg-white shadow-[0_20px_50px_rgba(15,23,42,0.12)] lg:grid-cols-[1.05fr_0.95fr]">
        <a
          href="http://localhost:3000/"
          className="absolute right-5 top-5 z-10 inline-flex items-center gap-2 rounded-full border border-white/50 bg-black/25 px-4 py-2 text-sm font-semibold text-white backdrop-blur hover:bg-black/40"
        >
          <ArrowLeft className="h-4 w-4" />
          返回导航
        </a>

        <div className="flex min-h-[280px] flex-col justify-end bg-[linear-gradient(160deg,#c1121f_0%,#ae0f1b_58%,#780000_100%)] px-8 py-12 text-white lg:px-14">
          <p className="mb-2 text-xs font-bold uppercase tracking-[0.28em] text-white/70">Stock Trading System</p>
          <h1 className="text-4xl font-bold leading-tight lg:text-6xl">账户管理系统</h1>
          <p className="mt-5 max-w-md text-sm leading-7 text-white/80 lg:text-base">
            面向工作人员的账户业务后台，负责证券账户、资金账户及相关业务办理。
          </p>
        </div>

        <div className="bg-white px-6 py-10 lg:px-12 lg:py-14">
          <div className="mx-auto max-w-md">
            <div className="mb-8">
              <p className="text-sm font-semibold uppercase tracking-[0.18em] text-red-600">Staff Console</p>
              <div className="mt-4 flex items-center gap-3">
                <div className="rounded-full bg-red-600 p-3 text-white">
                  <Briefcase className="h-6 w-6" />
                </div>
                <div>
                  <h2 className="text-3xl font-bold text-slate-900">工作人员登录</h2>
                  <p className="mt-1 text-sm text-slate-500">请输入账户管理系统工作人员账号与密码</p>
                </div>
              </div>
            </div>

            <div className="space-y-5">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">工作人员账号</label>
                <input
                  data-testid="login-account"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                  placeholder="请输入工作人员账号"
                />
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">密码</label>
                <input
                  data-testid="login-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                  placeholder="请输入密码"
                  onKeyDown={(e) => e.key === "Enter" && handleLogin()}
                />
              </div>

              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
                  {error}
                </div>
              )}

              <Button
                data-testid="login-submit"
                className="h-11 w-full bg-red-600 text-base font-semibold text-white hover:bg-red-700"
                onClick={handleLogin}
                disabled={loading}
              >
                {loading ? "登录中..." : "登录"}
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
