import { fetchSession } from "./authApi";

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

let cached: Session | null = null;

/** root clientLoader 가 채우고 하위 layout loader 가 읽는다. */
export function setSession(session: Session): void {
  cached = session;
}

/**
 * 이미 확정된 세션을 돌려준다. root clientLoader 가 먼저 실행돼 캐시를 채워 두므로,
 * 하위 layout loader 는 세션을 다시 조회하지 않는다.
 */
export async function requireSession(): Promise<Session> {
  if (cached) return cached;
  const response = await fetchSession();
  cached = response ? { status: "authenticated", ...response } : ANONYMOUS;
  return cached;
}
