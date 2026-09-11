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
let pending: Promise<Session> | null = null;

/**
 * protected 레이아웃의 clientLoader 가 채우고, 로그인·로그아웃·단계 갱신 뒤 다시 채운다.
 *
 * 이론상 위험해 보이는 경합 하나 — `pending` 이 아직 진행 중인데 `setSession` 이
 * `cached` 를 먼저 채우면, 나중에 `pending` 이 resolve 하며 `requireSession` 안의
 * `.then` 이 `cached` 를 자신의(오래된) 값으로 다시 덮어쓰지 않을까? — 는 실제로는
 * 일어나지 않는다. 이 모듈 밖에서 `setSession` 을 부르는 모든 자리(온보딩 네 화면의
 * `clientAction` 등)가 그 직전에 `await requireSession()` 을 먼저 부르고, 그 호출이
 * `pending` 이 있으면 끝까지 기다려 비운 뒤에야 반환한다 — 따라서 `setSession` 이
 * 실행되는 시점에는 `pending` 이 이미 `null` 이다. 검토했고 의도적으로 닫아 둔
 * 경합이다 — 재발견하지 않도록 남겨 둔다.
 */
export function setSession(session: Session): void {
  cached = session;
}

/** 테스트 전용 — 모듈 캐시와 진행 중인 조회를 지운다. 프로덕션 코드는 부르지 않는다. */
export function resetSession(): void {
  cached = null;
  pending = null;
}

/**
 * 이미 확정된 세션을 돌려준다. React Router 는 매칭된 layout 들의 loader 를 병렬로 돌리므로,
 * `protected.tsx` 의 clientLoader 와 하위 layout loader 가 캐시가 비어 있는 채로 동시에 이
 * 함수를 부를 수 있다 — in-app 네비게이션에서는 protected 쪽이 먼저 끝나 있는 게 보통이지만
 * 보장되지는 않는다. 그래서 진행 중인 조회를 `pending` 에 공유해 fetchSession 이 한 번만
 * 나가게 한다.
 */
export async function requireSession(): Promise<Session> {
  if (cached) return cached;
  if (!pending) {
    pending = fetchSession()
      .then((response): Session =>
        response ? { status: "authenticated", ...response } : ANONYMOUS,
      )
      // 서버에 닿지 못한 것과 비로그인을 구분하지 않는다. 어느 쪽이든 보호 화면은 못 연다.
      .catch((): Session => ANONYMOUS)
      .then((session) => {
        cached = session;
        pending = null;
        return session;
      });
  }
  return pending;
}
