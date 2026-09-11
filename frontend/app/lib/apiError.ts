import type { ApiError } from "./coreApi";

const GENERIC_MESSAGE =
  "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

/**
 * `coreApi` 가 정규화해 던지는 `ApiError` 구조를 판별한다 — 이 프로젝트에서
 * 유일한 판별 구현이다(이전에는 `income.tsx`·`simulator.tsx` 가 각자 비슷한
 * 조건을 다시 적었다). 호출부가 특정 status 만 더 좁혀 쓰고 싶으면 이 함수로
 * 먼저 좁힌 뒤 `.status` 를 직접 비교한다.
 */
export function isApiError(error: unknown): error is ApiError {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    "message" in error &&
    "status" in error
  );
}

/**
 * `coreApi` 가 던지는 오류를 화면에 그대로 보여줘도 되는 문자열로 좁힌다.
 * `coreApi` 가 정규화한 `ApiError` 만 그 `message` 를 쓴다 — `coreApi` 가 이미
 * 한국어로 다듬어 준다(서버가 JSON 을 못 준 502 등의 대체 문구 포함). 그 밖의
 * 오류(네트워크 예외 등)는 `code`·`status` 를 화면에 노출하지 않도록 고정
 * 문구로 떨어뜨린다. `UnauthorizedError` 는 이 함수에 오기 전에 호출부가 먼저
 * 잡아 로그인으로 보내야 한다 — 여기서는 다루지 않는다.
 */
export function toUserMessage(error: unknown): string {
  return isApiError(error) ? error.message : GENERIC_MESSAGE;
}
