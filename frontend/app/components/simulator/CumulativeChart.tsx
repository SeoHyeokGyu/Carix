import { useState } from "react";

import { cumulativeGap } from "../../lib/simulatorCompare";
import { amountLabel, niceTicks } from "./chartScale";

// 좁은 화면(약 340px)에서 viewBox 와 거의 1:1 로 그려져 글자가 12px 안팎이 되게 폭을 줄였다.
const WIDTH = 360;
const HEIGHT = 240;
const PAD = { left: 52, right: 56, top: 22, bottom: 30 };
const FONT = 12;
const MONTHS_PER_YEAR = 12;
const Y_PADDING_RATIO = 0.12;
const TOOLTIP_W = 150;
const TOOLTIP_H = 40;
// 툴팁이 오른쪽 끝값 라벨 자리(PAD.right)까지 넘어가도 되는 여유.
const TOOLTIP_OVERHANG = 40;
// 차이가 전부 0 이어도 눈금이 쓸 만한 간격이 되게 하는 최소 범위(원).
const FLAT_SPAN = 1_000_000;
// 글자(12 단위)가 넓은 컨테이너에서 16px 을 넘게 커지지 않도록 그림 폭을 제한한다.
const MAX_WIDTH_CLASS = "max-w-[480px]";

export interface CumulativeChartProps {
  /** 월별 누적(원). 인덱스 i 는 i+1 개월째. */
  stay: readonly number[];
  move: readonly number[];
  /** 새 회사 누적이 지금 회사 누적 이상이 되는 첫 달(1 부터). 없으면 null. */
  crossoverMonth: number | null;
}

export function CumulativeChart({
  stay,
  move,
  crossoverMonth,
}: CumulativeChartProps) {
  const [pointer, setPointer] = useState<number | null>(null);
  const gap = cumulativeGap(stay, move);
  const months = gap.length;
  if (months === 0) return null;

  const lo = Math.min(...gap, 0);
  const hi = Math.max(...gap, 0);
  const pad = (hi - lo || FLAT_SPAN) * Y_PADDING_RATIO;
  const ticks = niceTicks(lo - pad, hi + pad, 4);
  const yMin = Math.min(lo - pad, ticks[0]);
  const yMax = Math.max(hi + pad, ticks[ticks.length - 1]);

  const plotW = WIDTH - PAD.left - PAD.right;
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const x = (month: number) => PAD.left + (month / months) * plotW;
  const y = (v: number) =>
    PAD.top + plotH - ((v - yMin) / (yMax - yMin || 1)) * plotH;
  const points = [
    `${x(0)},${y(0)}`,
    ...gap.map((v, i) => `${x(i + 1)},${y(v)}`),
  ].join(" ");

  const yearMarks = Array.from(
    { length: Math.floor(months / MONTHS_PER_YEAR) },
    (_, i) => (i + 1) * MONTHS_PER_YEAR,
  );

  const crossAt =
    crossoverMonth !== null && crossoverMonth >= 1 && crossoverMonth <= months
      ? crossoverMonth
      : null;

  const endValue = gap[months - 1];
  const ariaLabel = `새 회사로 옮겼을 때 누적 차이 그래프. ${
    crossAt !== null ? `${crossAt}개월째 따라잡음` : "4년 안에 못 따라잡음"
  }, 4년 끝 ${amountLabel(endValue)}원.`;

  const onMove = (event: React.PointerEvent<SVGSVGElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width === 0) return;
    const px = ((event.clientX - rect.left) / rect.width) * WIDTH;
    const month = Math.round(((px - PAD.left) / plotW) * months);
    setPointer(Math.min(months, Math.max(1, month)));
  };

  const tipX =
    pointer === null
      ? 0
      : Math.min(
          WIDTH - PAD.right - TOOLTIP_W + TOOLTIP_OVERHANG,
          Math.max(PAD.left, x(pointer) + 10),
        );

  return (
    <figure className="m-0">
      <svg
        role="img"
        aria-label={ariaLabel}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className={`h-auto w-full touch-pan-y ${MAX_WIDTH_CLASS}`}
        onPointerMove={onMove}
        onPointerLeave={() => setPointer(null)}
      >
        {yMin < 0 && (
          <rect
            x={PAD.left}
            y={y(0)}
            width={plotW}
            height={y(yMin) - y(0)}
            className="fill-carix-danger/10"
          />
        )}
        {ticks.map((t) => (
          <g key={t}>
            <line
              x1={PAD.left}
              x2={WIDTH - PAD.right}
              y1={y(t)}
              y2={y(t)}
              className={
                t === 0 ? "stroke-carix-text/70" : "stroke-carix-border"
              }
              strokeWidth={t === 0 ? 1.5 : 1}
            />
            <text
              x={PAD.left - 6}
              y={y(t)}
              textAnchor="end"
              dominantBaseline="central"
              fontSize={FONT}
              className="fill-carix-text/70"
            >
              {t === 0 ? "0" : amountLabel(t)}
            </text>
          </g>
        ))}
        {yearMarks.map((m) => (
          <g key={m}>
            <line
              x1={x(m)}
              x2={x(m)}
              y1={PAD.top}
              y2={PAD.top + plotH}
              className="stroke-carix-border"
              strokeDasharray="2 4"
            />
            <text
              x={x(m)}
              y={HEIGHT - 10}
              textAnchor="middle"
              fontSize={FONT}
              className="fill-carix-text/70"
            >
              {m / MONTHS_PER_YEAR}년
            </text>
          </g>
        ))}

        <polyline
          points={points}
          fill="none"
          strokeWidth={3}
          strokeLinejoin="round"
          className="stroke-carix-action"
        />
        <text
          x={x(months) + 5}
          y={y(endValue)}
          dominantBaseline="central"
          fontSize={FONT}
          fontWeight={700}
          className="fill-carix-action"
        >
          {amountLabel(endValue)}
        </text>

        {crossAt !== null && (
          <g>
            <circle
              cx={x(crossAt)}
              cy={y(gap[crossAt - 1])}
              r={6}
              strokeWidth={2.5}
              className="fill-carix-surface stroke-carix-action"
            />
            <text
              x={x(crossAt)}
              y={y(gap[crossAt - 1]) - 12}
              textAnchor="middle"
              fontSize={FONT}
              fontWeight={700}
              className="fill-carix-action"
            >
              {crossAt}개월
            </text>
          </g>
        )}

        {pointer !== null && (
          <g pointerEvents="none">
            <line
              x1={x(pointer)}
              x2={x(pointer)}
              y1={PAD.top}
              y2={PAD.top + plotH}
              className="stroke-carix-text/40"
            />
            <circle
              cx={x(pointer)}
              cy={y(gap[pointer - 1])}
              r={4}
              className="fill-carix-action"
            />
            <rect
              x={tipX}
              y={PAD.top + 4}
              width={TOOLTIP_W}
              height={TOOLTIP_H}
              rx={6}
              className="fill-carix-surface stroke-carix-border"
            />
            <text
              x={tipX + 8}
              y={PAD.top + 20}
              fontSize={FONT}
              className="fill-carix-text/70"
            >
              {pointer}개월째 차이
            </text>
            <text
              x={tipX + 8}
              y={PAD.top + 37}
              fontSize={FONT}
              fontWeight={700}
              className="fill-carix-text"
            >
              {amountLabel(gap[pointer - 1])}
            </text>
          </g>
        )}
      </svg>
      <figcaption className="mt-2 text-sm text-carix-text/70">
        {crossAt !== null
          ? `처음엔 쉬는 기간 때문에 내려갔다가 ${crossAt}개월째 따라잡습니다.`
          : "4년 안에 0 위로 올라오지 못합니다."}
      </figcaption>
    </figure>
  );
}
