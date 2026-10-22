import type { InflationBasis } from "./api/scenario";

const MAN_WON = 10_000;

function signed(value: number): string {
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  return `${sign}${Math.abs(value).toLocaleString("ko-KR")}`;
}

/** 원 → 만원 반올림. 부호를 붙인다. */
export function signedMan(won: number): string {
  return `${signed(Math.round(won / MAN_WON))}만`;
}

export function stayHeadline(pct: number): string {
  const rounded = Math.round(pct * 10) / 10;
  const sign = rounded > 0 ? "+" : rounded < 0 ? "-" : "";
  return `남으면 실질 소득 ${sign}${Math.abs(rounded).toFixed(1)}%`;
}

export function moveHeadline(
  firstYearTotal: number,
  crossoverMonth: number | null,
): { headline: string; note: string | null } {
  const first = `이직 첫해 ${signedMan(firstYearTotal)}`;
  if (crossoverMonth === null) {
    return { headline: first, note: "비교 기간 4년 안에 역전되지 않습니다" };
  }
  return { headline: `${first}, ${crossoverMonth}개월째 역전`, note: null };
}

/** 역전이 있을 때만 조언을 낸다. */
export function moveAdvice(crossoverMonth: number | null): string | null {
  if (crossoverMonth === null) return null;
  return `새 직장을 ${crossoverMonth}개월 이상 다닐 생각이면 이직이 유리합니다`;
}

export function inflationNote(basis: InflationBasis): string | null {
  if (basis.personalPercent === null) {
    return "개인물가를 계산할 수 없어 정부 전망을 사용했습니다";
  }
  if (basis.similar && basis.gapPp !== null) {
    return `정부 전망과 차이 ${Math.abs(basis.gapPp).toFixed(1)}%p — 소비구조가 평균과 비슷합니다`;
  }
  return null;
}

export function severanceNote(w: {
  monthsToAnniversary: number;
  estimatedAmount: number;
}): string {
  const man = Math.round(w.estimatedAmount / MAN_WON).toLocaleString("ko-KR");
  return `${w.monthsToAnniversary}개월만 더 다니면 퇴직금 약 ${man}만원이 생깁니다`;
}

const UNREFLECTED_LABELS: Record<string, string> = {
  PERFORMANCE_BONUS: "성과급",
  STOCK: "주식",
  HIRE_DATE: "입사일",
  MOVE_PERFORMANCE_BONUS: "이직처 성과급",
  SIGNING_BONUS: "사이닝 보너스",
  MOVE_STOCK: "이직처 주식",
};

/** 모르는 코드는 숨기지 않고 코드 그대로 보인다. */
export function unreflectedLabel(code: string): string {
  return UNREFLECTED_LABELS[code] ?? code;
}
