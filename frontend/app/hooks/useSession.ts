import { ANONYMOUS, type Session } from "../lib/session";

/**
 * 현재 세션 상태.
 *
 * TODO: (cjkang) `GET /api/v1/auth/session` 응답으로 교체한다. 지금은 `/api` 프록시가 없어
 * (README 개발 환경 절) 항상 비로그인을 돌려준다. 교체 지점은 이 훅 하나이고
 * 가드 규칙(`lib/routeGuards.ts`)은 바뀌지 않는다.
 */
export function useSession(): Session {
  return ANONYMOUS;
}
