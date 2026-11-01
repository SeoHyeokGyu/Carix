import { CircleHelp } from "lucide-react";
import { redirect, useLoaderData, useLocation } from "react-router";

import { AppShell, type NavItem } from "../../components/layout/AppShell";
import { GuestBanner } from "../../components/layout/GuestBanner";
import { guardOnboarded, LANDING_PATH } from "../../lib/routeGuards";
import { requireSession } from "../../lib/session";

/**
 * 주 메뉴는 보는 화면만 둔다. 입력 관리(S-12)는 처음 한 번 넣고 바뀔 때만 고치는 화면이라
 * 설정·로그아웃과 함께 `AccountMenu` 에 있다. 지난 판정(S-11)은
 * 판정의 하위 화면이라 주 메뉴가 아니라 판정 히어로·시장가치 카드의 링크로 들어가고,
 * 그 화면에서는 `match` 로 「대시보드」가 선택된 채 보인다.
 */
export const MAIN_NAV_ITEMS: NavItem[] = [
  {
    to: "/dashboard",
    label: "대시보드",
    end: true,
    match: ["/dashboard/history"],
  },
  { to: "/dashboard/trends", label: "추이 분석" },
  { to: "/dashboard/skills", label: "스킬 분석" },
  { to: "/dashboard/simulator", label: "시뮬레이터" },
];

export const MAIN_UTILITY_ITEMS: NavItem[] = [
  { to: "/dashboard/guide", label: "도움말", icon: CircleHelp },
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
  return { guest: session.status === "authenticated" && session.guest };
}

export default function DashboardLayout() {
  const { guest } = useLoaderData<typeof clientLoader>();
  const { pathname } = useLocation();
  return (
    <>
      {guest && <GuestBanner pathname={pathname} />}
      <AppShell
        navLabel="주 메뉴"
        items={MAIN_NAV_ITEMS}
        utilityItems={MAIN_UTILITY_ITEMS}
        accountMenu
      />
    </>
  );
}
