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
