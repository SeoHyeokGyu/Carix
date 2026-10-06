export type ExploreTab = "monthly" | "postings" | "cpi" | "wage";

export const TABS: { id: ExploreTab; label: string }[] = [
  { id: "monthly", label: "월별 현황" },
  { id: "postings", label: "공고" },
  { id: "cpi", label: "CPI" },
  { id: "wage", label: "임금통계" },
];

/** 표본 목표 250 의 80%. 설계 §8 강조 규칙 — collector 점검 기준과 같다. */
export const SAMPLE_WARN_BELOW = 200;
/** 시장가치 동료집단 최소 표본. */
export const FAMILY_MIN_SAMPLE = 10;

export function parseTab(value: string | null): ExploreTab {
  return TABS.some((t) => t.id === value) ? (value as ExploreTab) : "monthly";
}

export const isThinSample = (n: number) => n < SAMPLE_WARN_BELOW;
export const isThinFamily = (n: number) => n < FAMILY_MIN_SAMPLE;

const WON_PER_MANWON = 10_000;

/** 원 단위 금액 → 「6,123만원」(만원 미만 버림). */
export function formatManwon(won: number): string {
  return `${Math.floor(won / WON_PER_MANWON).toLocaleString("ko-KR")}만원`;
}

/** 공고 제시 연봉 범위. 있는 쪽만 보이고 둘 다 없으면 「미공개」. */
export function formatSalaryRange(
  min: number | null,
  max: number | null,
): string {
  if (min == null && max == null) return "미공개";
  if (min != null && max != null) {
    return min === max
      ? formatManwon(min)
      : `${formatManwon(min)}~${formatManwon(max)}`;
  }
  return min != null
    ? `${formatManwon(min)}~`
    : `~${formatManwon(max as number)}`;
}
