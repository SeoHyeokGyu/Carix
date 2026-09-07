import type { OnboardingStep } from "./session";

/** auth-service 는 ApiResult 로 감싸지 않는다 (설계 §4.3). */
export async function advanceOnboarding(step: OnboardingStep) {
  const response = await fetch("/api/v1/auth/onboarding", {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentStep: step }),
  });
  if (!response.ok) {
    throw new Error("온보딩 단계를 갱신하지 못했습니다.");
  }
  return (await response.json()) as { currentStep: string; completed: boolean };
}
