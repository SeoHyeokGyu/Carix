import type { OnboardingStep, Session } from "./session";

export const LANDING_PATH = "/";
export const LOGIN_PATH = "/login";
export const DASHBOARD_PATH = "/dashboard";
export const REVIEW_PATH = "/onboarding/review";

/** 온보딩 단계와 화면 경로의 대응. 검토(REVIEW)는 완료 후에도 재진입을 허용한다. */
const STEP_PATH: Record<Exclude<OnboardingStep, "COMPLETED">, string> = {
  PROFILE: "/onboarding/profile",
  INCOME: "/onboarding/income",
  EXPENSE: "/onboarding/expense",
  SKILLS: "/onboarding/skills",
  REVIEW: REVIEW_PATH,
};

export type GuardResult =
  | { allow: true }
  | { allow: false; redirectTo: string }
  /** 세션 조회 중. 아직 판정하지 않는다 — 여기서 튕기면 로그인한 사용자도 랜딩을 한 번 본다. */
  | { allow: "pending" };

const ALLOW: GuardResult = { allow: true };
const PENDING: GuardResult = { allow: "pending" };
const redirect = (redirectTo: string): GuardResult => ({
  allow: false,
  redirectTo,
});

export function stepPath(step: OnboardingStep): string {
  return step === "COMPLETED" ? DASHBOARD_PATH : STEP_PATH[step];
}

/**
 * 세션만 요구한다 (마이페이지). 온보딩 완료 여부는 보지 않는다.
 *
 * 세션이 없으면 랜딩으로 보낸다. 로그인 화면이 아니다 — 처음 온 사람은 가입해야 하고,
 * 랜딩이 가입과 로그인을 모두 안내한다.
 */
export function guardSession(session: Session): GuardResult {
  if (session.status === "loading") return PENDING;
  return session.status === "anonymous" ? redirect(LANDING_PATH) : ALLOW;
}

/** 세션과 온보딩 완료를 모두 요구한다 (대시보드). 미완료면 남은 단계로 보낸다. */
export function guardOnboarded(session: Session): GuardResult {
  if (session.status === "loading") return PENDING;
  if (session.status === "anonymous") return redirect(LANDING_PATH);
  if (session.onboardingStep !== "COMPLETED") {
    return redirect(stepPath(session.onboardingStep));
  }
  return ALLOW;
}

/**
 * 온보딩 화면 진입 규칙.
 *
 * - 세션이 없으면 랜딩으로
 * - 완료했으면 대시보드로. 단 검토 화면은 재진입을 허용한다
 * - 미완료인데 다른 단계를 열면 마지막 미완료 단계로 되돌린다
 */
export function guardOnboarding(
  session: Session,
  pathname: string,
): GuardResult {
  if (session.status === "loading") return PENDING;
  if (session.status === "anonymous") return redirect(LANDING_PATH);

  if (session.onboardingStep === "COMPLETED") {
    return pathname === REVIEW_PATH ? ALLOW : redirect(DASHBOARD_PATH);
  }

  const current = stepPath(session.onboardingStep);
  return pathname === current ? ALLOW : redirect(current);
}
