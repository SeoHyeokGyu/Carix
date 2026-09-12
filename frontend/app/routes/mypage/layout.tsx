import { redirect } from "react-router";
import { AppShell } from "../../components/layout/AppShell";
import { guardSession, LANDING_PATH } from "../../lib/routeGuards";
import { requireSession } from "../../lib/session";
import { MAIN_NAV_ITEMS, MAIN_UTILITY_ITEMS } from "../dashboard/layout";

/** 마이페이지는 온보딩 완료를 요구하지 않는다 — 중단한 사용자도 계정 삭제는 할 수 있어야 한다. */
export async function clientLoader() {
  const session = await requireSession();
  const guard = guardSession(session);
  // guard.allow === "pending" 은 여기서 실제로 나오지 않는다 — requireSession 이 이미
  // 세션을 확정해 돌려준다. 그래도 fail-closed 로 처리한다: allow 가 true 가 아니면 무조건
  // 막는다(랜딩으로).
  if (guard.allow !== true) {
    throw redirect(guard.allow === false ? guard.redirectTo : LANDING_PATH);
  }
  return null;
}

export default function MypageLayout() {
  return (
    <AppShell
      navLabel="마이페이지"
      items={MAIN_NAV_ITEMS}
      utilityItems={MAIN_UTILITY_ITEMS}
    />
  );
}
