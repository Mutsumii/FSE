import { createBrowserRouter, useRouteError } from "react-router";
import { Layout } from "./components/Layout";
import { RequireAuth } from "./components/RequireAuth";
import Dashboard from "./pages/Dashboard";
import FundAccounts from "./pages/FundAccounts";
import CertificateAuth from "./pages/CertificateAuth";
import Login from "./pages/Login";
import SecuritiesAccounts from "./pages/SecuritiesAccounts";

function ErrorBoundary() {
  const error = useRouteError() as Error | undefined;
  console.error(error);

  return (
    <div className="p-4 text-red-600">
      <h1 className="mb-2 text-xl font-bold">页面加载失败</h1>
      <pre className="overflow-auto rounded bg-red-50 p-4">
        {error?.message || "Unknown error"}
      </pre>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    path: "/login",
    Component: Login,
    errorElement: <ErrorBoundary />,
  },
  {
    path: "/certificate",
    Component: CertificateAuth,
    errorElement: <ErrorBoundary />,
  },
  {
    Component: () => <RequireAuth mode="admin" />,
    errorElement: <ErrorBoundary />,
    children: [
      {
        path: "/",
        Component: Layout,
        children: [
          { index: true, Component: Dashboard },
          { path: "securities", Component: SecuritiesAccounts },
          { path: "funds", Component: FundAccounts },
        ],
      },
    ],
  },
]);
