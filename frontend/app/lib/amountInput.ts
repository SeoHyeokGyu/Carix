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

const MANWON = 10_000;

/**
 * 만원 칸에서 다시 제출된 금액(원)이 저장값을 반올림해 보여준 그대로면 원래 원 단위 값을 돌려준다.
 * 화면이 만원 단위로 바뀌기 전에 원 단위로 저장된 값이, 사용자가 손대지 않았는데 반올림 값으로
 * 바뀌어 저장되지 않게 한다. 고쳤거나 저장값이 없으면 입력값 그대로다.
 */
export function keepUntouchedAmount(
  enteredWon: number,
  savedWon: number | null,
): number {
  if (savedWon === null) return enteredWon;
  return Math.round(savedWon / MANWON) * MANWON === enteredWon
    ? savedWon
    : enteredWon;
}

/** 폼의 숨은 칸(`saved-<카테고리>`)에 실어 둔 저장값(원)을 읽는다. 없으면 `null`. */
export function savedAmountFrom(
  formData: FormData,
  category: string,
): number | null {
  return parseAmountInput(String(formData.get(`saved-${category}`) ?? ""));
}
