import { redirect } from "react-router";
import { AppShell, type NavItem } from "../../components/layout/AppShell";
import { guardOnboarded, LANDING_PATH } from "../../lib/routeGuards";
import { requireSession } from "../../lib/session";

const TABS: NavItem[] = [
  { to: "/dashboard", label: "지표", end: true },
  { to: "/dashboard/history", label: "이력" },
  { to: "/dashboard/trends", label: "추이 분석" },
  { to: "/dashboard/simulator", label: "시뮬레이터" },
];

export async function clientLoader() {
  const session = await requireSession();
  const guard = guardOnboarded(session);
  // guard.allow === "pending" 은 여기서 실제로 나오지 않는다 — requireSession 이 이미
  // 세션을 확정해 돌려준다. 그래도 fail-closed 로 처리한다: allow 가 true 가 아니면 무조건
  // 막는다(랜딩으로).
  if (guard.allow !== true) {
    throw redirect(guard.allow === false ? guard.redirectTo : LANDING_PATH);
  }
  return null;
}

export default function DashboardLayout() {
  return <AppShell navLabel="대시보드" items={TABS} />;
}
