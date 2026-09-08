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
 * `STEP_PATH` 의 선언 순서를 그대로 온보딩 진행 순서로 쓴다 — 별도 순서 배열을
 * 새로 만들지 않는다. 두 목록이 따로 있으면 한쪽만 고치고 잊는 실수가 난다.
 * 객체 리터럴의 문자열 키는 선언 순서를 유지하므로 안전하게 이 순서를 쓸 수 있다.
 */
const STEP_ORDER = Object.keys(STEP_PATH) as Exclude<OnboardingStep, "COMPLETED">[];

function stepIndex(step: Exclude<OnboardingStep, "COMPLETED">): number {
  return STEP_ORDER.indexOf(step);
}

function stepForPath(pathname: string): Exclude<OnboardingStep, "COMPLETED"> | undefined {
  return STEP_ORDER.find((step) => STEP_PATH[step] === pathname);
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
 * - 미완료 상태에서 현재 단계보다 앞선(아직 도달하지 않은) 단계를 열면 현재
 *   단계로 되돌린다
 * - 미완료 상태에서 현재 단계와 같거나 그 이전 단계를 여는 것은 허용한다 —
 *   검토(S-09) 화면의 "수정" 링크가 각 섹션의 앞선 단계로 되돌아가야 하기
 *   때문이다(이 화면의 "이전" 링크도 같은 이유로 필요하다)
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
  if (pathname === current) return ALLOW;

  // pathname 이 알려진 온보딩 단계 경로가 아니면(오타 등) fail-closed 로 현재
  // 단계로 되돌린다 — 아래 인덱스 비교를 할 수 없기 때문이다.
  const targetStep = stepForPath(pathname);
  if (targetStep === undefined) return redirect(current);

  return stepIndex(targetStep) <= stepIndex(session.onboardingStep)
    ? ALLOW
    : redirect(current);
}
