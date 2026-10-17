import type { SalaryTrendPoint } from "./dashboardMarketApi";
import type { SparklinePoint, SparklineSeries } from "./verdict";

const YEAR_MONTH_PATTERN = /^(\d{4})-(\d{2})$/;

function monthNumber(yearMonth: string): number | null {
  const match = YEAR_MONTH_PATTERN.exec(yearMonth);
  return match ? Number(match[1]) * 12 + Number(match[2]) : null;
}

/**
 * 등록월별 중앙 제시 연봉을 스파크라인 계열로. 가장 오래된 달이 0 번 칸이다.
 * 표본 부족(`null`)·0 이하·빠진 달에서 선을 끊는다 — 인접월 값으로 채우지 않는다(기능 명세 US-D3).
 */
export function toSalaryTrendSeries(
  trend: readonly SalaryTrendPoint[],
): SparklineSeries {
  const placed = trend.flatMap((point) => {
    const month = monthNumber(point.yearMonth);
    return month === null ? [] : [{ month, median: point.median }];
  });
  if (placed.length === 0) return { slots: 0, segments: [] };

  const oldest = Math.min(...placed.map((p) => p.month));
  const newest = Math.max(...placed.map((p) => p.month));
  const byIndex = new Map(placed.map((p) => [p.month - oldest, p.median]));
  const slots = newest - oldest + 1;

  const segments: SparklinePoint[][] = [];
  let current: SparklinePoint[] = [];
  for (let index = 0; index < slots; index++) {
    const value = byIndex.get(index);
    if (value == null || value <= 0) {
      if (current.length > 0) segments.push(current);
      current = [];
      continue;
    }
    current = [...current, { index, value }];
  }
  if (current.length > 0) segments.push(current);
  return { slots, segments };
}
