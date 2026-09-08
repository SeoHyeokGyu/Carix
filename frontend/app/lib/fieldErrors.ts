/**
 * 필드별 오류 메시지. `_form` 은 특정 필드에 붙일 수 없는 오류(저장 API 실패,
 * 메시지 매핑이 없는 경로, 경로 자체가 없는 전체 폼 이슈 등)를 위한 예약 키다.
 * 인덱스 시그니처를 열어 두는 이유는 반복 필드(`items.0.amount` 같은 경로)를
 * 갖는 화면도 이 타입을 그대로 쓸 수 있어야 하기 때문이다 — 특정 스크린의
 * 필드 키 유니온에 묶지 않는다.
 */
export interface FieldErrors {
  _form?: string;
  [field: string]: string | undefined;
}

const GENERIC_FIELD_MESSAGE = "입력값을 다시 확인해 주세요.";

/**
 * zod 이슈의 `path` 전체를 하나의 키로 합친다 — `["items", 0, "amount"]` 는
 * `"items.0.amount"` 가 된다. 첫 세그먼트만 보면 `items.0.amount` 와
 * `items.1.amount` 를 구분하지 못해 반복 필드 화면에서 한 행의 오류가 다른
 * 행에도 덮어써지거나 전부 하나로 뭉개진다. 경로가 비어 있으면(전체 폼 이슈)
 * `undefined` 를 돌려준다 — 특정 필드에 붙일 자리가 없다는 뜻이다. symbol
 * 세그먼트도 마찬가지다(`Array.prototype.join` 이 symbol 을 문자열로 바꾸다
 * 던진다 — zod 이슈에 실제로 나올 일은 없지만 방어적으로 걸러 둔다).
 */
function pathToKey(path: readonly PropertyKey[]): string | undefined {
  if (path.length === 0) return undefined;
  if (path.some((segment) => typeof segment === "symbol")) return undefined;
  return path.join(".");
}

/**
 * zod 이슈 목록 → 필드별 오류 메시지.
 *
 * 키를 만들 수 없는 이슈(경로가 없거나 symbol 세그먼트가 섞인 경우)와, 키는
 * 있지만 `messages` 에 매핑이 없는 이슈(화면에 아직 그 필드의 표시 자리가
 * 없거나 메시지 매핑을 빠뜨린 경우)를 조용히 버리면 사용자에게는 아무 피드백도
 * 없이 저장 버튼만 죽은 채로 남는다 — 둘 다 `_form` 에 일반 오류를 올려 최소한
 * 실패했다는 사실은 알린다. 이미 필드별 메시지든 `_form` 이든 한 번 채워진
 * 자리는 다시 채우지 않는다(첫 이슈만 대표로 남긴다).
 */
export function toFieldErrors(
  issues: readonly { path: PropertyKey[] }[],
  messages: Record<string, string>,
): FieldErrors {
  const errors: FieldErrors = {};
  for (const issue of issues) {
    const key = pathToKey(issue.path);
    if (key !== undefined && key in errors) continue;

    const message = key !== undefined ? messages[key] : undefined;
    if (key !== undefined && message) {
      errors[key] = message;
    } else if (!errors._form) {
      errors._form = GENERIC_FIELD_MESSAGE;
    }
  }
  return errors;
}
