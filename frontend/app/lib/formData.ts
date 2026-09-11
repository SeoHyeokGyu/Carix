export interface FormDataToObjectOptions {
  /**
   * 이 이름들은 문자열 대신 숫자로 파싱한다. 빈 문자열이거나 파싱 결과가
   * `Number.isFinite` 를 통과하지 못하면(빈 값·`NaN`·`Infinity`) 키 자체를
   * 만들지 않는다.
   */
  numeric?: readonly string[];
  /**
   * 데이터가 아닌 필드(폼 제출 의도를 나르는 필드 등)는 이 목록에 넣어 제외한다.
   * 기본값 `["intent"]` — 이 프로젝트의 두-버튼 제출 화면들이 공통으로 쓰는
   * 히든 필드 이름이다.
   */
  exclude?: readonly string[];
}

/**
 * `FormData` → 후보 객체. 빈 문자열(미입력·미선택)은 키 자체를 만들지 않는다 —
 * `.partial()` 스키마조차 "필드가 있는데 값이 비었다"를 오류로 잡기 때문에,
 * 부분 저장이 성립하려면 미입력 필드가 객체에 아예 없어야 한다.
 *
 * 항목 개수를 미리 알 수 없는 반복 필드(예: 소비 입력 화면의 `items.0.amount`)
 * 도 다루도록 특정 스키마의 키를 미리 알 필요 없이 `FormData` 의 모든 항목을
 * 그대로 순회한다. 스키마에 없는 키는 이후 zod 의 기본 strip 동작(알 수 없는
 * 키를 조용히 버리는 것)이 걸러낸다 — 그래도 `intent` 처럼 애초에 데이터가
 * 아닌 필드는 이 단계에서 걸러 둔다.
 */
export function formDataToObject(
  formData: FormData,
  options: FormDataToObjectOptions = {},
): Record<string, unknown> {
  const numeric = new Set(options.numeric ?? []);
  const exclude = new Set(options.exclude ?? ["intent"]);
  const raw: Record<string, unknown> = {};

  for (const [key, value] of formData.entries()) {
    if (exclude.has(key) || typeof value !== "string" || value === "") continue;

    if (numeric.has(key)) {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) raw[key] = parsed;
      continue;
    }

    raw[key] = value;
  }

  return raw;
}
