import { redirect } from "react-router";
import { AppShell, type NavItem } from "../../components/layout/AppShell";
import { guardOnboarding, LANDING_PATH } from "../../lib/routeGuards";
import { requireSession } from "../../lib/session";

const STEPS: NavItem[] = [
  { to: "/onboarding/profile", label: "프로필" },
  { to: "/onboarding/income", label: "소득" },
  { to: "/onboarding/expense", label: "소비" },
  { to: "/onboarding/skills", label: "스킬" },
  { to: "/onboarding/review", label: "검토" },
];

export async function clientLoader({ request }: { request: Request }) {
  const session = await requireSession();
  const guard = guardOnboarding(session, new URL(request.url).pathname);
  // guard.allow === "pending" 은 여기서 실제로 나오지 않는다 — requireSession 이 이미
  // 세션을 확정해 돌려준다. 그래도 fail-closed 로 처리한다: allow 가 true 가 아니면 무조건
  // 막는다(랜딩으로).
  if (guard.allow !== true) {
    throw redirect(guard.allow === false ? guard.redirectTo : LANDING_PATH);
  }
  return null;
}

export default function OnboardingLayout() {
  return <AppShell navLabel="온보딩 단계" items={STEPS} />;
}
