/**
 * 추이 분석(S-14) 탭 키. URL `?tab=` 값이라 대시보드 카드 링크가 특정 탭으로 바로 보낸다.
 * 스킬 수요 섹션은 스킬 분석(S-18, `/dashboard/skills`)으로 이동했다.
 */
export const TRENDS_TABS = [
  { key: "income", label: "소득 vs 물가" },
  { key: "salary", label: "연봉 분포" },
] as const;

export type TrendsTabKey = (typeof TRENDS_TABS)[number]["key"];

export const DEFAULT_TRENDS_TAB: TrendsTabKey = "income";

/** 모르는 값·빈 값은 기본 탭으로 접는다. */
export function resolveTrendsTab(value: string | null): TrendsTabKey {
  return TRENDS_TABS.some((tab) => tab.key === value)
    ? (value as TrendsTabKey)
    : DEFAULT_TRENDS_TAB;
}

export function trendsTabHref(tab: TrendsTabKey): string {
  return `/dashboard/trends?tab=${tab}`;
}
