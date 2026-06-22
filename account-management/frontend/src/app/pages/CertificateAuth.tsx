import { useEffect, useState } from "react";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { api } from "../lib/api";

export default function CertificateAuth() {
  const navigate = useNavigate();
  const [certificateCode, setCertificateCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const pending = api.getPendingCertificate();

  useEffect(() => {
    if (!pending) {
      navigate("/login", { replace: true });
    }
  }, [navigate, pending]);

  const handleSubmit = async () => {
    const trimmedCode = certificateCode.trim();
    if (!trimmedCode) {
      setError("请输入安全证书认证码");
      return;
    }

    setLoading(true);
    setError("");
    try {
      await api.completeCertificate(trimmedCode);
      navigate("/", { replace: true });
    } catch (err: any) {
      setError(err.message || "安全证书认证失败");
    } finally {
      setLoading(false);
    }
  };

  if (!pending) {
    return null;
  }

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
          <h1 className="text-4xl font-bold leading-tight lg:text-6xl">首次证书认证</h1>
          <p className="mt-5 max-w-md text-sm leading-7 text-white/80 lg:text-base">
            工作人员首次登录账户管理系统前，需要先完成一次安全证书认证。
          </p>
        </div>

        <div className="bg-white px-6 py-10 lg:px-12 lg:py-14">
          <div className="mx-auto max-w-md">
            <div className="mb-8">
              <p className="text-sm font-semibold uppercase tracking-[0.18em] text-red-600">Security Check</p>
              <div className="mt-4 flex items-center gap-3">
                <div className="rounded-full bg-red-600 p-3 text-white">
                  <ShieldCheck className="h-6 w-6" />
                </div>
                <div>
                  <h2 className="text-3xl font-bold text-slate-900">安全证书认证</h2>
                  <p className="mt-1 text-sm text-slate-500">认证对象：{pending.subjectKey}</p>
                </div>
              </div>
            </div>

            <div className="space-y-5">
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                <div>演示认证码：`CERT-123456`</div>
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">安全证书认证码</label>
                <input
                  data-testid="certificate-code"
                  type="password"
                  value={certificateCode}
                  onChange={(e) => setCertificateCode(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm shadow-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                  placeholder="请输入安全证书认证码"
                  onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
                />
              </div>

              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
                  {error}
                </div>
              )}

              <div className="flex gap-3">
                <Button
                  variant="outline"
                  className="h-11 flex-1"
                  onClick={() => {
                    api.clearAllSessions();
                    navigate("/login", { replace: true });
                  }}
                >
                  返回登录
                </Button>
                <Button
                  data-testid="certificate-submit"
                  className="h-11 flex-1 bg-red-600 text-white hover:bg-red-700"
                  onClick={handleSubmit}
                  disabled={loading}
                >
                  {loading ? "认证中..." : "完成认证"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
