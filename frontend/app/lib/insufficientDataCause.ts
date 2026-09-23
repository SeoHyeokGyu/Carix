/**
 * `데이터 부족` 스냅샷의 원인이 사용자 입력으로 해소되는지 판정한다.
 *
 * 서버 `DecisionSignalEngine` 이 `rationale.rulesApplied` 에 넣는 문구를 그대로 본다. 스냅샷이
 * 보존하는 것은 문구뿐이고 제외 사유 코드는 저장되지 않아, 화면이 기댈 수 있는 신호가 이것뿐이다.
 *
 * NOTE: (cjkang) 아래 상수는 서버 `DecisionSignalEngine.MARKET_COVERAGE_GAP` 과 같은 문자열이어야
 * 한다. 한쪽만 바꾸면 조용히 입력 유도가 다시 뜬다. 계약 테스트로 고정한다.
 */
export const MARKET_COVERAGE_GAP = "시장 집계 미수집 구간";

/**
 * 시장 쪽 사정이라 사용자가 무엇을 입력해도 달라지지 않는 원인들.
 *
 * NOTE: (cjkang) `동료집단 표본 수가 통계 기준치 미만` 과 `연봉 격차 기준선(임금통계) 미확보` 도
 * 같은 성격이지만, 그 두 문구의 화면 동작을 바꾸는 것은 별도 판단이라 여기 넣지 않았다.
 */
const MARKET_SIDE_CAUSES: readonly string[] = [MARKET_COVERAGE_GAP];

/** 입력 유도를 띄워도 되는 원인인지. 규칙 문구가 없으면 기존 동작(유도 표시)을 유지한다. */
export function isUserActionable(rulesApplied: readonly string[] | undefined) {
  if (!rulesApplied || rulesApplied.length === 0) return true;
  return !rulesApplied.some((rule) => MARKET_SIDE_CAUSES.includes(rule));
}
