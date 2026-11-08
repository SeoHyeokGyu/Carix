import { useState } from "react";

import { useElementWidth } from "../../hooks/useElementWidth";
import type {
  WageGrowthPoint,
  WageGrowthRun,
} from "../../lib/dashboardMarketApi";
import { formatKoreanSalary } from "../../lib/koreanSalary";

// 차트는 실제 폭에 1:1 로 그린다 — 고정 viewBox 를 늘리면 넓은 화면에서 높이·글자가 같이 부푼다.
const FALLBACK_WIDTH = 640;
const HEIGHT = 280;
const FONT = 12;
const PADDING = { left: 40, top: 16, bottom: 32 };
const NARROW_PADDING_RIGHT = 16;
// 선 끝 라벨(「내 연봉 112.5」) 자리.
const LABEL_PADDING_RIGHT = 96;
// 이보다 좁으면 선 끝 라벨을 빼고 그 자리를 그래프에 준다 — 범례와 아래 표가 같은 말을 한다.
const END_LABEL_MIN_WIDTH = 480;
const BASELINE = 100;
// 전부 100 의 약수라 기준선이 늘 눈금에 놓인다.
const TICK_STEPS = [5, 10, 25, 50, 100];
const MAX_TICK_INTERVALS = 8;
const POINT_RADIUS = 4;
const END_LABEL_GAP = 7;

const MINE_COLOR = "var(--color-carix-info)";
const MARKET_COLOR = "var(--color-carix-market)";
const SURFACE = "var(--carix-surface)";

interface Scale {
  x: (year: number) => number;
  y: (value: number) => number;
}

function tickStep(range: number): number {
  const fitting = TICK_STEPS.find((s) => range / s <= MAX_TICK_INTERVALS);
  if (fitting) return fitting;
  const largest = TICK_STEPS[TICK_STEPS.length - 1];
  return Math.ceil(range / (largest * MAX_TICK_INTERVALS)) * largest;
}

function yBounds(points: readonly WageGrowthPoint[]) {
  const values = points.flatMap((p) => [p.myIndex, p.marketIndex]);
  const low = Math.min(BASELINE, ...values);
  const high = Math.max(BASELINE, ...values);
  const step = tickStep(high - low);
  const min = Math.floor(low / step) * step;
  const max = Math.max(Math.ceil(high / step) * step, min + step);
  const ticks: number[] = [];
  for (let value = min; value <= max; value += step) ticks.push(value);
  return { min, max, ticks };
}

function linePoints(
  points: readonly WageGrowthPoint[],
  scale: Scale,
  value: (point: WageGrowthPoint) => number,
): string {
  return points
    .map((p) => `${scale.x(Number(p.year))},${scale.y(value(p))}`)
    .join(" ");
}

export interface WageGrowthChartProps {
  runs: readonly WageGrowthRun[];
}

/** 내 연봉과 같은 경력 구간 임금의 누적 지수. 실행마다 100 에서 시작하고 실행 사이는 잇지 않는다. */
export function WageGrowthChart({ runs }: WageGrowthChartProps) {
  const [hoveredYear, setHoveredYear] = useState<string | null>(null);
  const [chartRef, width] = useElementWidth<HTMLDivElement>(FALLBACK_WIDTH);

  const points = runs.flatMap((run) => run.points);
  const firstYear = Number(points[0].year);
  const lastPoint = points[points.length - 1];
  const lastYear = Number(lastPoint.year);

  const showsEndLabels = width >= END_LABEL_MIN_WIDTH;
  const paddingRight = showsEndLabels
    ? LABEL_PADDING_RIGHT
    : NARROW_PADDING_RIGHT;
  const plotWidth = width - PADDING.left - paddingRight;
  const plotHeight = HEIGHT - PADDING.top - PADDING.bottom;
  const { min, max, ticks } = yBounds(points);
  const scale: Scale = {
    x: (year) =>
      PADDING.left +
      ((year - firstYear) / Math.max(lastYear - firstYear, 1)) * plotWidth,
    y: (value) => PADDING.top + ((max - value) / (max - min)) * plotHeight,
  };

  const mineIsUpper = lastPoint.myIndex >= lastPoint.marketIndex;
  const endLabelY = (value: number, isUpper: boolean) =>
    scale.y(value) + (isUpper ? -END_LABEL_GAP : END_LABEL_GAP + FONT);
  const hovered = points.find((p) => p.year === hoveredYear) ?? null;

  return (
    <div ref={chartRef} className="relative">
      <div className="mb-2 flex flex-wrap items-center gap-3 text-xs">
        <div className="flex items-center gap-1.5">
          <span className="inline-block h-0.5 w-4 rounded bg-carix-info" />
          <span className="font-medium text-carix-text">내 연봉</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="inline-block h-0.5 w-4 border-t-2 border-dashed border-carix-market" />
          <span className="font-medium text-carix-text">
            같은 경력 구간 임금 중앙값
          </span>
        </div>
      </div>

      <svg
        role="img"
        aria-label="내 연봉과 같은 경력 구간 임금의 누적 지수. 시작 연도 100 기준"
        width={width}
        height={HEIGHT}
        viewBox={`0 0 ${width} ${HEIGHT}`}
      >
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              x1={PADDING.left}
              x2={width - paddingRight}
              y1={scale.y(tick)}
              y2={scale.y(tick)}
              stroke={
                tick === BASELINE
                  ? "var(--color-carix-text)"
                  : "var(--color-carix-border)"
              }
              strokeOpacity={tick === BASELINE ? 0.4 : 1}
            />
            <text
              x={PADDING.left - 8}
              y={scale.y(tick) + FONT / 3}
              textAnchor="end"
              fontSize={FONT}
              fontWeight={tick === BASELINE ? 700 : 400}
              fill="var(--color-carix-text)"
              fillOpacity={0.7}
            >
              {tick}
            </text>
          </g>
        ))}

        {points.map((p) => (
          <text
            key={`year-${p.year}`}
            x={scale.x(Number(p.year))}
            y={HEIGHT - 10}
            textAnchor="middle"
            fontSize={FONT}
            fill="var(--color-carix-text)"
            fillOpacity={0.7}
          >
            {p.year}
          </text>
        ))}

        {runs.map((run) => (
          <g key={`run-${run.points[0].year}`}>
            <polyline
              points={linePoints(run.points, scale, (p) => p.marketIndex)}
              fill="none"
              stroke={MARKET_COLOR}
              strokeWidth={2}
              strokeDasharray="6 4"
            />
            <polyline
              points={linePoints(run.points, scale, (p) => p.myIndex)}
              fill="none"
              stroke={MINE_COLOR}
              strokeWidth={2}
            />
          </g>
        ))}

        {points.map((p) => {
          const x = scale.x(Number(p.year));
          return (
            <g
              key={`point-${p.year}`}
              className="cursor-pointer"
              onMouseEnter={() => setHoveredYear(p.year)}
              onMouseLeave={() => setHoveredYear(null)}
            >
              <circle
                cx={x}
                cy={scale.y(p.marketIndex)}
                r={POINT_RADIUS}
                fill={MARKET_COLOR}
                stroke={SURFACE}
                strokeWidth={1.5}
              />
              {/* 기록 없는 해는 빈 점 — 동결인지 미입력인지 모른다 */}
              <circle
                cx={x}
                cy={scale.y(p.myIndex)}
                r={POINT_RADIUS}
                fill={p.recorded ? MINE_COLOR : SURFACE}
                stroke={p.recorded ? SURFACE : MINE_COLOR}
                strokeWidth={p.recorded ? 1.5 : 2}
              />
              <rect
                x={x - 16}
                y={PADDING.top}
                width={32}
                height={plotHeight}
                fill="transparent"
              />
            </g>
          );
        })}

        {showsEndLabels && (
          <>
            <text
              x={scale.x(lastYear) + 10}
              y={endLabelY(lastPoint.marketIndex, !mineIsUpper)}
              fontSize={FONT}
              fill="var(--color-carix-text)"
            >
              {`시장 ${lastPoint.marketIndex.toFixed(1)}`}
            </text>
            <text
              x={scale.x(lastYear) + 10}
              y={endLabelY(lastPoint.myIndex, mineIsUpper)}
              fontSize={FONT}
              fill="var(--color-carix-text)"
            >
              {`내 연봉 ${lastPoint.myIndex.toFixed(1)}`}
            </text>
          </>
        )}
      </svg>

      {hovered && (
        <div
          className="pointer-events-none absolute top-8 z-10 rounded-lg border border-carix-border bg-carix-surface p-3 text-xs shadow-md"
          style={{
            left: `${Math.min(75, Math.max(15, ((Number(hovered.year) - firstYear) / Math.max(lastYear - firstYear, 1)) * 100))}%`,
            transform: "translateX(-50%)",
          }}
        >
          <div className="mb-1.5 border-b border-carix-border pb-1 font-bold text-carix-text">
            {hovered.year}
          </div>
          <TooltipRow label="내 연봉 지수" value={hovered.myIndex.toFixed(1)} />
          <TooltipRow
            label="시장 지수"
            value={hovered.marketIndex.toFixed(1)}
          />
          <div className="mt-1.5 border-t border-carix-border/60 pt-1.5 text-[11px]">
            {hovered.myGross != null ? (
              <TooltipRow
                label="연 소득 (고정상여 포함)"
                value={formatKoreanSalary(hovered.myGross)}
              />
            ) : (
              <p className="text-carix-text/70">변동 기록 없음</p>
            )}
            <TooltipRow
              label="통계청 중앙값"
              value={formatKoreanSalary(hovered.marketMedian)}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function TooltipRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="text-carix-text/80">{label}</span>
      <strong className="text-carix-text">{value}</strong>
    </div>
  );
}
