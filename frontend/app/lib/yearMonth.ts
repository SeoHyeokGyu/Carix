/**
 * 소비 관리의 "당월" 기준값. `expense.tsx`(쓰기)와 `review.tsx`(읽기)가 각자
 * 계산하던 것을 하나로 모았다 — 둘이 따로 계산하면 월 경계를 걸쳐 호출되는
 * 드문 순간(자정 직후 등)에 서로 다른 월을 가리킬 수 있고, 저장 직후 검토
 * 화면이 "입력된 소비 내역이 없습니다"로 잘못 보일 수 있다.
 */
export function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, "0")}`;
}

const WIRE_PATTERN = /^\d{6}$/;

/**
 * 와이어 형식(`YYYYMM`)을 사람이 읽는 `YYYY-MM` 으로 바꾼다. 형식이 아니면 받은
 * 값을 그대로 돌려준다 — 서버가 규약을 어겨도 화면에 빈 칸이 뜨는 것보다 원본이
 * 보이는 편이 낫다.
 */
export function formatYearMonth(wire: string): string {
  if (!WIRE_PATTERN.test(wire)) return wire;
  return `${wire.slice(0, 4)}-${wire.slice(4)}`;
}

/** `YYYY-MM`(`<input type="month">` 값) 을 와이어 형식으로 바꾼다. */
export function toWireYearMonth(display: string): string {
  return display.replace("-", "");
}

/**
 * 당월보다 뒤인지 본다. 백엔드는 `^\d{6}$` 만 보고 미래를 안 막으므로
 * (`ExpenseController`), 기능 명세 US-R1 의 "미래 날짜 입력 거부"는 여기서 지킨다.
 * 형식이 아닌 값은 미래로 보지 않는다 — 형식 오류는 스키마가 따로 잡는다.
 */
export function isFutureYearMonth(wire: string): boolean {
  if (!WIRE_PATTERN.test(wire)) return false;
  return wire > currentYearMonth();
}
