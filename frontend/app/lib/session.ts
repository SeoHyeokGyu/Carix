/** 백엔드 `users.onboarding_step` 과 같은 값이다 (상세 기술 명세 §1.2). */
export type OnboardingStep =
  "PROFILE" | "INCOME" | "EXPENSE" | "SKILLS" | "REVIEW" | "COMPLETED";

export type Session =
  | { status: "anonymous" }
  | { status: "authenticated"; email: string; onboardingStep: OnboardingStep };

export const ANONYMOUS: Session = { status: "anonymous" };
