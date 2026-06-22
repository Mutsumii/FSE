import { Navigate, Outlet, useLocation } from "react-router";
import { api } from "../lib/api";

type RequireAuthProps = {
  mode: "admin";
};

export function RequireAuth({ mode }: RequireAuthProps) {
  const location = useLocation();
  const isAllowed = mode === "admin" ? api.isStaffLoggedIn() : false;

  if (!isAllowed) {
    return <Navigate to="/login" replace state={{ from: location.pathname, mode }} />;
  }

  return <Outlet />;
}
