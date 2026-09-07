import { redirect } from "react-router";
import { AppShell, type NavItem } from "../../components/layout/AppShell";
import { guardSession } from "../../lib/routeGuards";
import { requireSession } from "../../lib/session";

const TABS: NavItem[] = [
  { to: "/mypage/records", label: "이력 관리" },
  { to: "/mypage/profile", label: "설정" },
];

/** 마이페이지는 온보딩 완료를 요구하지 않는다 — 중단한 사용자도 계정 삭제는 할 수 있어야 한다. */
export async function clientLoader() {
  const session = await requireSession();
  const guard = guardSession(session);
  // guard.allow === "pending" 은 여기서 나올 수 없다 — requireSession 이 이미 세션을
  // 확정해 돌려준다.
  if (guard.allow === false) {
    throw redirect(guard.redirectTo);
  }
  return null;
}

export default function MypageLayout() {
  return <AppShell navLabel="마이페이지" items={TABS} />;
}
