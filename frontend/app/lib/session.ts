/** 백엔드 `users.onboarding_step` 과 같은 값이다 (상세 기술 명세 §1.2). */
export type OnboardingStep =
  "PROFILE" | "INCOME" | "EXPENSE" | "SKILLS" | "REVIEW" | "COMPLETED";

/**
 * 세션 상태.
 *
 * `loading` 이 있어야 첫 렌더에서 잘못 튕기지 않는다. 세션 조회는 비동기라, 응답 전에
 * `anonymous` 로 취급하면 로그인한 사용자도 랜딩으로 한 번 튕겼다가 돌아온다.
 */
export type Session =
  | { status: "loading" }
  | { status: "anonymous" }
  | {
      status: "authenticated";
      userId: string;
      email: string;
      onboardingStep: OnboardingStep;
    };

export const LOADING: Session = { status: "loading" };
export const ANONYMOUS: Session = { status: "anonymous" };

/** `GET /api/v1/auth/session` 응답 형식 (API 규격 §2.3). */
export interface SessionResponse {
  userId: string;
  email: string;
  onboardingStep: OnboardingStep;
}
