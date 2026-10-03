import type { SparklineSeries } from "../../lib/verdict";

const WIDTH = 120;
const HEIGHT = 32;
const PADDING = 2;
const BASELINE = 100;
/** 반폭의 하한. 이보다 좁히면 몇 포인트 변화가 위아래로 치솟아 보인다. */
const MIN_HALF_SPAN = 10;

/**
 * 세로축 범위. 최소·최대로 채우지 않고 기준 100 을 가운데 둔 대칭 범위를 쓴다 —
 * 지표 카드 눈금(`IndexCard` BaselineScale)과 같은 기준으로 크기를 읽게 한다.
 */
export function sparklineDomain(values: readonly number[]): {
  min: number;
  max: number;
} {
  const halfSpan = Math.max(
    MIN_HALF_SPAN,
    ...values.map((value) => Math.abs(value - BASELINE)),
  );
  return { min: BASELINE - halfSpan, max: BASELINE + halfSpan };
}

/** 값이 이어진 구간마다 선을 따로 긋는다 — 구간 사이는 잇지 않는다. 한 점짜리 구간은 점으로. */
export function Sparkline({ series }: { series: SparklineSeries }) {
  const values = series.segments.flat().map((point) => point.value);
  const { min, max } = sparklineDomain(values);
  const range = max - min;
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
      <line
        data-testid="sparkline-baseline"
        x1={0}
        x2={WIDTH}
        y1={y(BASELINE)}
        y2={y(BASELINE)}
        className="stroke-carix-text/30"
        strokeWidth={1}
        strokeDasharray="2 2"
      />
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
