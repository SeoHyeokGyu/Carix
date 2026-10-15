/**
 * `S-10` 1층(판정)이 쓰는 계산. 설계 `2026-09-22-dashboard-verdict-first-design.md` §4.
 *
 * `/v1/dashboard/history` 는 최신 달이 앞에 온다 — 연속 개월은 그 순서로 세고,
 * 스파크라인은 뒤집어 오래된 달을 왼쪽에 둔다.
 */
import type { SnapshotPoint } from "./dashboardMarketApi";

const WIRE_PATTERN = /^\d{6}$/;
const BASE_DATE_PATTERN = /^(\d{4})-(\d{2})/;
const HEADING_SUFFIX = "내 커리어 판정";

export const SPARKLINE_MONTHS = 12;

export interface SparklinePoint {
  /** 창 안의 자리. 0 이 가장 오래된 달이다. */
  index: number;
  value: number;
}

export interface SparklineSeries {
  /** 창의 달 수 — x 축 칸 수. */
  slots: number;
  /** 값이 이어진 구간들. 구간 사이는 잇지 않는다. */
  segments: SparklinePoint[][];
}

export function formatVerdictDescription(baseDate: string | undefined): string {
  const match = baseDate ? BASE_DATE_PATTERN.exec(baseDate) : null;
  if (!match) return HEADING_SUFFIX;
  return `${match[1]}년 ${Number(match[2])}월 기준 · ${HEADING_SUFFIX}`;
}

export function previousYearMonth(wire: string): string | null {
  if (!WIRE_PATTERN.test(wire)) return null;
  const year = Number(wire.slice(0, 4));
  const month = Number(wire.slice(4));
  if (month === 1) return `${year - 1}12`;
  return `${year}${String(month - 1).padStart(2, "0")}`;
}

/** 최신 달부터 `currentCode` 가 끊김 없이 이어진 개월 수. 달력상 빈 달도 끊김이다. */
export function countSignalStreak(
  snapshots: readonly SnapshotPoint[],
  currentCode: string,
): number {
  let count = 0;
  let expectedMonth: string | null = null;
  for (const point of snapshots) {
    if (point.decisionSignal !== currentCode) break;
    if (expectedMonth !== null && point.baseYearMonth !== expectedMonth) break;
    count += 1;
    expectedMonth = previousYearMonth(point.baseYearMonth);
    if (expectedMonth === null) break;
  }
  return count;
}

/** Convert YYYYMM string to month number for calendar arithmetic. */
function monthNumber(wire: string): number {
  const year = Number(wire.slice(0, 4));
  const month = Number(wire.slice(4));
  return year * 12 + month;
}

interface PointWithOffset {
  value: number;
  offset: number;
}

/**
 * 시장가치 지수의 최근 `months` 개월. `null` 과 0 이하는 값이 아니다 — 기준 100 인
 * 지수에서 0 은 만들어진 숫자다(설계 §9). 달력상 빈 달도 선을 끊는다.
 * 각 점은 최신 달으로부터의 오프셋으로 배치된다.
 */
export function toSparklineSeries(
  snapshots: readonly SnapshotPoint[],
  months: number = SPARKLINE_MONTHS,
): SparklineSeries {
  if (snapshots.length === 0) {
    return { slots: 0, segments: [] };
  }

  const newestMonth = monthNumber(snapshots[0].baseYearMonth);

  // First pass: collect valid points with offsets and find max offset
  const points: PointWithOffset[] = [];
  let maxOffset = -1;

  for (const snap of snapshots) {
    const offset = newestMonth - monthNumber(snap.baseYearMonth);
    if (offset >= months) break;

    const value = snap.marketValueIndex;
    const hasValue = value !== null && value > 0;

    if (hasValue) {
      points.push({ value, offset });
    } else {
      points.push({ value: 0, offset }); // Marker for gap
    }
    // Update maxOffset for ALL included snapshots, not just valid ones
    maxOffset = offset;
  }

  const slots = maxOffset === -1 ? 0 : maxOffset + 1;

  // Second pass: build segments using correct slots
  const segments: SparklinePoint[][] = [];
  let current: SparklinePoint[] = [];

  for (let i = 0; i < points.length; i++) {
    const point = points[i];
    const isGap = point.value === 0;

    if (isGap || point.offset > (i > 0 ? points[i - 1].offset : -1) + 1) {
      // Gap or missing month
      if (current.length > 0) {
        // Sort by index (oldest to newest)
        segments.push(current.sort((a, b) => a.index - b.index));
      }
      current = [];
    }

    if (!isGap) {
      const index = slots - 1 - point.offset;
      current = [...current, { index, value: point.value }];
    }
  }

  if (current.length > 0) {
    segments.push(current.sort((a, b) => a.index - b.index));
  }

  // Reverse segments since we built them newest-to-oldest
  segments.reverse();

  return { slots, segments };
}
