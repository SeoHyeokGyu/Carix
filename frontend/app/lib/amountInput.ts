/**
 * 금액 입력칸 공용 처리. 화면은 천 단위 콤마를 붙여 보여주고, 제출할 때는 콤마를
 * 걷어낸 숫자 문자열로 바꿔 `formDataToObject` 의 숫자 파싱에 넘긴다.
 */

/** 숫자만 남겨 천 단위 콤마를 붙인다. 숫자가 하나도 없으면 빈 문자열이다. */
export function formatAmountInput(value: string): string {
  const digits = value.replace(/[^0-9]/g, "");
  return digits === "" ? "" : Number(digits).toLocaleString("ko-KR");
}

/** 콤마를 붙인 입력값을 숫자로 읽는다. 비어 있으면 `null` 이다. */
export function parseAmountInput(value: string): number | null {
  const digits = value.replace(/[^0-9]/g, "");
  return digits === "" ? null : Number(digits);
}

/**
 * `names` 필드의 콤마를 걷어내고 `scale` 을 곱한 새 `FormData` 를 돌려준다. 만원
 * 단위로 받는 칸은 `scale` 에 `10_000` 을 넘긴다. 빈 칸은 빈 문자열로 남겨
 * `formDataToObject` 가 미입력으로 처리하게 둔다.
 */
export function withPlainAmounts(
  formData: FormData,
  names: readonly string[],
  scale = 1,
): FormData {
  const targets = new Set(names);
  const next = new FormData();
  for (const [key, value] of formData.entries()) {
    if (!targets.has(key) || typeof value !== "string") {
      next.append(key, value);
      continue;
    }
    const amount = parseAmountInput(value);
    next.append(key, amount === null ? "" : String(amount * scale));
  }
  return next;
}
