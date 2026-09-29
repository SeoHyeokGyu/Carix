import type { SparklineSeries } from "../../lib/verdict";

const WIDTH = 120;
const HEIGHT = 32;
const PADDING = 2;

/** 값이 이어진 구간마다 선을 따로 긋는다 — 구간 사이는 잇지 않는다. 한 점짜리 구간은 점으로. */
export function Sparkline({ series }: { series: SparklineSeries }) {
  const values = series.segments.flat().map((point) => point.value);
  const min = Math.min(...values);
  const range = Math.max(...values) - min || 1;
  const x = (index: number) =>
    series.slots <= 1 ? WIDTH / 2 : (index / (series.slots - 1)) * WIDTH;
  const y = (value: number) =>
    HEIGHT - PADDING - ((value - min) / range) * (HEIGHT - PADDING * 2);

  return (
    <svg
      data-testid="sparkline"
      aria-hidden="true"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      className="h-8 w-30 text-carix-info"
    >
      {series.segments.map((segment) =>
        segment.length === 1 ? (
          <circle
            key={segment[0].index}
            cx={x(segment[0].index)}
            cy={y(segment[0].value)}
            r={1.5}
            fill="currentColor"
          />
        ) : (
          <polyline
            key={segment[0].index}
            points={segment.map((p) => `${x(p.index)},${y(p.value)}`).join(" ")}
            fill="none"
            stroke="currentColor"
            strokeWidth={1.5}
            strokeLinejoin="round"
          />
        ),
      )}
    </svg>
  );
}
