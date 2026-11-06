/**
 * 추이 분석(S-14) 섹션 앵커. 대시보드 카드 링크가 `#id` 로 특정 섹션에 바로 보낸다.
 * 스킬 수요 섹션은 스킬 분석(S-18, `/dashboard/skills`)으로 이동했다.
 */
export const TRENDS_SECTIONS = {
  income: { id: "income", title: "지수 추이" },
  growth: { id: "growth", title: "상승률 비교" },
  salary: { id: "salary", title: "동료집단 연봉 분포" },
} as const;

export type TrendsSectionKey = keyof typeof TRENDS_SECTIONS;

export function trendsSectionHref(section: TrendsSectionKey): string {
  return `/dashboard/trends#${TRENDS_SECTIONS[section].id}`;
}
