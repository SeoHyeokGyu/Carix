import { Navigate, useLocation } from "react-router";
import { AppShell, type NavItem } from "../../components/layout/AppShell";
import { useSession } from "../../hooks/useSession";
import { guardOnboarding } from "../../lib/routeGuards";

const STEPS: NavItem[] = [
  { to: "/onboarding/profile", label: "프로필" },
  { to: "/onboarding/income", label: "소득" },
  { to: "/onboarding/expense", label: "소비" },
  { to: "/onboarding/skills", label: "스킬" },
  { to: "/onboarding/review", label: "검토" },
];

export default function OnboardingLayout() {
  const session = useSession();
  const { pathname } = useLocation();
  const guard = guardOnboarding(session, pathname);

  if (!guard.allow) {
    return <Navigate to={guard.redirectTo} replace />;
  }

  return <AppShell navLabel="온보딩 단계" items={STEPS} />;
}
