import { Outlet, redirect } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";

import { AppShell } from "../../components/layout/AppShell";
import { StepIndicator } from "../../components/onboarding/StepIndicator";
import { guardOnboarding, LANDING_PATH } from "../../lib/routeGuards";
import { requireSession } from "../../lib/session";

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
  return (
    <AppShell navLabel="온보딩" items={[]}>
      {/* 화면 내에 일관된 온보딩 Step 인디케이터 표시 */}
      <div className="w-full border-b border-carix-border/60 bg-carix-surface/40 backdrop-blur-xs py-4 sm:py-6">
        <PageFrame className="py-0 sm:py-0">
          <StepIndicator />
        </PageFrame>
      </div>
      <Outlet />
    </AppShell>
  );
}
