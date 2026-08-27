import { Navigate } from "react-router";
import { AppShell, type NavItem } from "../../components/layout/AppShell";
import { useSession } from "../../hooks/useSession";
import { guardSession } from "../../lib/routeGuards";

const TABS: NavItem[] = [
  { to: "/mypage/records", label: "이력 관리" },
  { to: "/mypage/profile", label: "설정" },
];

/** 마이페이지는 온보딩 완료를 요구하지 않는다 — 중단한 사용자도 계정 삭제는 할 수 있어야 한다. */
export default function MypageLayout() {
  const session = useSession();
  const guard = guardSession(session);

  if (!guard.allow) {
    return <Navigate to={guard.redirectTo} replace />;
  }

  return <AppShell navLabel="마이페이지" items={TABS} />;
}
