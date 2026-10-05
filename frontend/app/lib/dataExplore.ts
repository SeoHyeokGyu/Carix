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
