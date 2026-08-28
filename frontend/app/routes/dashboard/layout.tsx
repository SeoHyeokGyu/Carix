import { Navigate } from "react-router";
import { AppShell, type NavItem } from "../../components/layout/AppShell";
import { SessionPending } from "../../components/layout/SessionPending";
import { useSession } from "../../lib/SessionProvider";
import { guardOnboarded } from "../../lib/routeGuards";

const TABS: NavItem[] = [
  { to: "/dashboard", label: "지표", end: true },
  { to: "/dashboard/history", label: "이력" },
  { to: "/dashboard/trends", label: "추이 분석" },
  { to: "/dashboard/simulator", label: "시뮬레이터" },
];

export default function DashboardLayout() {
  const session = useSession();
  const guard = guardOnboarded(session);

  if (guard.allow === "pending") {
    return <SessionPending />;
  }
  if (!guard.allow) {
    return <Navigate to={guard.redirectTo} replace />;
  }

  return <AppShell navLabel="대시보드" items={TABS} />;
}
