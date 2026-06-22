import { useMemo, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router";
import { ArrowLeft, Bell, Building2, LayoutDashboard, LogOut, Menu, Settings, UserRound } from "lucide-react";
import { api } from "../lib/api";

export function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const navigate = useNavigate();

  const staffName = useMemo(() => api.getStaffUsername() || "工作人员", []);
  const staffInitial = staffName.charAt(0).toUpperCase() || "S";

  const handleLogout = () => {
    api.clearStaffSession();
    navigate("/login", { replace: true });
  };

  const navigation = [
    { name: "总览 Dashboard", href: "/", icon: LayoutDashboard, end: true },
    { name: "证券账户业务", href: "/securities", icon: Building2 },
    { name: "资金账户业务", href: "/funds", icon: UserRound },
  ];

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">
      <div
        className={`flex flex-shrink-0 flex-col bg-red-900 text-red-100 transition-all duration-300 ${
          sidebarOpen ? "w-64" : "w-20"
        }`}
      >
        <div className="flex h-16 items-center justify-center border-b border-red-800">
          <div className="flex items-center gap-2 text-xl font-bold text-white">
            <Building2 className="text-white" />
            {sidebarOpen && <span>StockSys</span>}
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto py-4">
          <ul className="space-y-1 px-2">
            {navigation.map((item) => (
              <li key={item.name}>
                <NavLink
                  to={item.href}
                  end={item.end}
                  className={({ isActive }) =>
                    `flex items-center gap-3 rounded-md px-3 py-2.5 transition-colors ${
                      isActive
                        ? "bg-white/20 font-medium text-white"
                        : "hover:bg-red-800 hover:text-white"
                    }`
                  }
                >
                  <item.icon className="h-5 w-5 flex-shrink-0" />
                  {sidebarOpen && <span>{item.name}</span>}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="border-t border-red-800 p-4">
          <a
            href="http://localhost:3000/"
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-red-200 transition-colors hover:bg-red-800 hover:text-white"
          >
            <ArrowLeft className="h-5 w-5 flex-shrink-0" />
            {sidebarOpen && <span>返回导航</span>}
          </a>
          <button className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-red-200 transition-colors hover:bg-red-800 hover:text-white">
            <Settings className="h-5 w-5 flex-shrink-0" />
            {sidebarOpen && <span>系统设置</span>}
          </button>
          <button
            onClick={handleLogout}
            className="mt-1 flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-red-200 transition-colors hover:bg-red-800 hover:text-white"
          >
            <LogOut className="h-5 w-5 flex-shrink-0" />
            {sidebarOpen && <span>安全退出</span>}
          </button>
        </div>
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6">
          <button onClick={() => setSidebarOpen(!sidebarOpen)} className="text-slate-500 hover:text-slate-700">
            <Menu className="h-6 w-6" />
          </button>

          <div className="flex items-center gap-4">
            <button className="relative text-slate-500 hover:text-slate-700">
              <Bell className="h-5 w-5" />
              <span className="absolute right-0 top-0 h-2 w-2 rounded-full bg-red-500" />
            </button>
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-red-100 font-bold text-red-700">
              {staffInitial}
            </div>
            <div className="text-sm font-medium text-slate-700">{staffName}</div>
          </div>
        </header>

        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
