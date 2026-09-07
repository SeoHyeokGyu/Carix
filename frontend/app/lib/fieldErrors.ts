/**
 * 필드별 오류 메시지. `_form` 은 특정 필드에 붙일 수 없는 오류(저장 API 실패,
 * 메시지 매핑이 없는 경로 등)를 위한 예약 키다. 인덱스 시그니처를 열어 두는
 * 이유는 반복 필드(`items.0.amount` 같은 경로)를 갖는 화면도 이 타입을 그대로
 * 쓸 수 있어야 하기 때문이다 — 특정 스크린의 필드 키 유니온에 묶지 않는다.
 */
export interface FieldErrors {
  _form?: string;
  [field: string]: string | undefined;
}

const GENERIC_FIELD_MESSAGE = "입력값을 다시 확인해 주세요.";

/**
 * zod 이슈 목록 → 필드별 오류 메시지.
 *
 * `messages` 에 없는 경로를 조용히 버리면(예: 화면에 아직 그 필드의 표시 자리가
 * 없거나 메시지 매핑을 빠뜨린 경우) 사용자에게는 아무 피드백도 없이 저장
 * 버튼만 죽은 채로 남는다 — 대신 `_form` 에 일반 오류를 올려 최소한 실패했다는
 * 사실은 알린다. 이미 매핑된 필드 오류가 있으면 `_form` 을 다시 채우지 않는다
 * (첫 매핑 실패만 대표로 알린다).
 */
export function toFieldErrors(
  issues: readonly { path: PropertyKey[] }[],
  messages: Record<string, string>,
): FieldErrors {
  const errors: FieldErrors = {};
  for (const issue of issues) {
    const field = issue.path[0];
    if (typeof field !== "string" || field in errors) continue;

    const message = messages[field];
    if (message) {
      errors[field] = message;
    } else if (!errors._form) {
      errors._form = GENERIC_FIELD_MESSAGE;
    }
  }
  return errors;
}
