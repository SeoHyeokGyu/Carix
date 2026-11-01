import type { InflationBasis } from "./api/scenario";
import { EXPENSE_CATEGORIES } from "./schemas/expense";

const MAN_WON = 10_000;

function signed(value: number): string {
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  return `${sign}${Math.abs(value).toLocaleString("ko-KR")}`;
}

/** 원 → 만원 반올림. 부호를 붙인다. */
export function signedMan(won: number): string {
  return `${signed(Math.round(won / MAN_WON))}만`;
}

/** 소수 첫째 자리 % 에 부호를 붙인다. */
export function signedPct(pct: number): string {
  const rounded = Math.round(pct * 10) / 10;
  const sign = rounded > 0 ? "+" : rounded < 0 ? "-" : "";
  return `${sign}${Math.abs(rounded).toFixed(1)}%`;
}

export function stayHeadline(pct: number): string {
  return `지금 회사 실질 소득 ${signedPct(pct)}`;
}

/** 격차는 (중앙값 − 내 연봉) / 내 연봉 이라 양수면 시장보다 덜 받는다. 대시보드 값과 같은 세전·명목이다. */
export function marketGapLine(gapPercent: number): string {
  const size = Math.abs(gapPercent).toFixed(1);
  if (size === "0.0") return "시장 중앙값과 같음 (세전 기준)";
  const side = gapPercent > 0 ? "낮음" : "높음";
  return `시장 중앙값보다 ${size}% ${side} (세전 기준)`;
}

const NO_CATCH_UP =
  "4년 안에 새 회사 누적이 지금 회사 누적을 따라잡지 못합니다";

/** 역전이 있을 때만 조언을 낸다. */
export function moveAdvice(crossoverMonth: number | null): string | null {
  if (crossoverMonth === null) return null;
  return `새 회사를 ${crossoverMonth}개월 이상 다닐 생각이면 옮기는 쪽이 유리합니다`;
}

/** 비교 결론의 큰 줄. 역전이 있으면 조언, 없으면 따라잡지 못한다는 문장. */
export function moveConclusion(crossoverMonth: number | null): string {
  return moveAdvice(crossoverMonth) ?? NO_CATCH_UP;
}

export function inflationNote(basis: InflationBasis): string | null {
  if (basis.personalPercent === null) {
    return "개인물가를 계산할 수 없어 한국은행 전망을 사용했습니다";
  }
  if (basis.similar && basis.gapPp !== null) {
    return `개인물가와 한국은행 전망 차이 ${similarGapLabel(basis.gapPp)} — 소비구조가 평균과 비슷합니다`;
  }
  return null;
}

/** 서버의 "비슷함" 기준(차이 0.3%p 미만). */
const SIMILAR_GAP_THRESHOLD_PP = 0.3;

/** 0.27 을 반올림해 "0.3%p — 비슷" 이라 쓰면 기준과 모순된다. 기준까지 반올림되면 "미만" 으로 적는다. */
function similarGapLabel(gapPp: number): string {
  const rounded = Number(Math.abs(gapPp).toFixed(1));
  return rounded >= SIMILAR_GAP_THRESHOLD_PP
    ? `${SIMILAR_GAP_THRESHOLD_PP.toFixed(1)}%p 미만`
    : `${rounded.toFixed(1)}%p`;
}

/** 모르는 카테고리는 코드 그대로 보인다. */
function categoryLabel(code: string): string {
  return EXPENSE_CATEGORIES.find((c) => c.value === code)?.label ?? code;
}

/**
 * 두 기준 차이가 클 때(`similar` 가 아님) 나란히 놓는 한 줄. 차이가 작거나 개인물가가 없으면 null — 그때는
 * {@link inflationNote} 가 맡는다.
 */
export function inflationGapLine(basis: InflationBasis): string | null {
  if (basis.personalPercent === null || basis.gapPp === null || basis.similar) {
    return null;
  }
  const head = `개인물가 ${basis.personalPercent.toFixed(1)}% · 한국은행 전망 ${basis.governmentPercent.toFixed(1)}% — 차이 ${Math.abs(basis.gapPp).toFixed(1)}%p`;
  if (basis.topGapCategory === null) return head;
  return `${head}, ${categoryLabel(basis.topGapCategory)} 비중 때문`;
}

/** 물가 출처와 기준 시점. 설정값이 오래돼도 시점을 그대로 보인다. */
export function inflationSource(basis: InflationBasis): string {
  return `출처: ${basis.source} (${basis.asOf} 발표)`;
}

export function severanceNote(w: {
  monthsToAnniversary: number;
  estimatedAmount: number;
}): string {
  const man = Math.round(w.estimatedAmount / MAN_WON).toLocaleString("ko-KR");
  return `${w.monthsToAnniversary}개월만 더 다니면 퇴직금 약 ${man}만원이 생깁니다`;
}
