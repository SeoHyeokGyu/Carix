import { UnauthorizedError } from "./coreApi";
import type { OnboardingStep } from "./session";

/**
 * auth-service 는 ApiResult 로 감싸지 않는다 (설계 §4.3).
 *
 * 401 은 `coreApi.unwrap` 과 같은 모양으로 `UnauthorizedError` 를 던진다 — 이 모듈만
 * 일반 `Error` 를 던지면 이 호출자(온보딩 네 화면)의 401 이 로그인 리다이렉트로
 * 이어지지 않고 화면에 일반 오류 배너만 뜬 채 죽은 세션에 남는다. 개발 계획 §5-B 의
 * 세션 게이트가 켜지면 세션 없는 모든 호출이 401 이 되므로 지금은 눈에 안 띄는
 * 경로다.
 */
export async function advanceOnboarding(step: OnboardingStep) {
  const response = await fetch("/api/v1/auth/onboarding", {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentStep: step }),
  });
  if (response.status === 401) {
    throw new UnauthorizedError();
  }
  if (!response.ok) {
    throw new Error("온보딩 단계를 갱신하지 못했습니다.");
  }
  return (await response.json()) as { currentStep: string; completed: boolean };
}
