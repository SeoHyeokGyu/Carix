import { useState } from "react";

import { signedMan } from "../../lib/simulatorCopy";
import { niceTicks } from "./chartScale";

const WIDTH = 640;
const HEIGHT = 300;
const PAD = { left: 60, right: 18, top: 24, bottom: 34 };
const MONTHS_PER_YEAR = 12;
const TOOLTIP_W = 196;
const TOOLTIP_H = 44;

export interface CumulativeChartProps {
  /** 월별 누적(원). 인덱스 i 는 i+1 개월째. */
  stay: readonly number[];
  move: readonly number[];
  /** 이직 누적이 잔류 누적 이상이 되는 첫 달(1 부터). 없으면 null. */
  crossoverMonth: number | null;
}

export function CumulativeChart({
  stay,
  move,
  crossoverMonth,
}: CumulativeChartProps) {
  const [pointer, setPointer] = useState<number | null>(null);
  const months = Math.min(stay.length, move.length);
  if (months === 0) return null;

  const all = [0, ...stay, ...move];
  const lo = Math.min(...all);
  const hi = Math.max(...all);
  const ticks = niceTicks(lo, hi, 4);
  const yMin = Math.min(lo, ticks[0]);
  const yMax = Math.max(hi, ticks[ticks.length - 1]);

  const plotW = WIDTH - PAD.left - PAD.right;
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const x = (month: number) => PAD.left + (month / months) * plotW;
  const y = (v: number) =>
    PAD.top + plotH - ((v - yMin) / (yMax - yMin || 1)) * plotH;
  const line = (series: readonly number[]) =>
    [
      `${x(0)},${y(0)}`,
      ...series.slice(0, months).map((v, i) => `${x(i + 1)},${y(v)}`),
    ].join(" ");

  const yearMarks = Array.from(
    { length: Math.floor(months / MONTHS_PER_YEAR) },
    (_, i) => (i + 1) * MONTHS_PER_YEAR,
  );
  const shadeEnd = Math.min(MONTHS_PER_YEAR, months);

  const crossAt =
    crossoverMonth !== null && crossoverMonth >= 1 && crossoverMonth <= months
      ? crossoverMonth
      : null;

  const last = months - 1;
  const ariaLabel = [
    `잔류와 이직의 ${months}개월 누적 세후 보상 비교 그래프.`,
    `${months}개월 시점 잔류 ${signedMan(stay[last])}원, 이직 ${signedMan(move[last])}원.`,
    crossAt !== null
      ? `이직 누적이 ${crossAt}개월째 잔류 누적을 따라잡습니다.`
      : `${Math.floor(months / MONTHS_PER_YEAR)}년 안에 역전되지 않습니다.`,
  ].join(" ");

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
          WIDTH - PAD.right - TOOLTIP_W,
          Math.max(PAD.left, x(pointer) + 10),
        );

  return (
    <figure className="m-0">
      <svg
        role="img"
        aria-label={ariaLabel}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="h-auto w-full touch-pan-y"
        onPointerMove={onMove}
        onPointerLeave={() => setPointer(null)}
      >
        <rect
          x={x(0)}
          y={PAD.top}
          width={x(shadeEnd) - x(0)}
          height={plotH}
          className="fill-carix-info/10"
        />
        <text
          x={x(0) + 6}
          y={PAD.top + 13}
          fontSize={11}
          className="fill-carix-text/55"
        >
          첫 12개월
        </text>
        {ticks.map((t) => (
          <g key={t}>
            <line
              x1={PAD.left}
              x2={WIDTH - PAD.right}
              y1={y(t)}
              y2={y(t)}
              className={
                t === 0 ? "stroke-carix-text/50" : "stroke-carix-border"
              }
              strokeWidth={1}
            />
            <text
              x={PAD.left - 8}
              y={y(t)}
              textAnchor="end"
              dominantBaseline="central"
              fontSize={11}
              className="fill-carix-text/60"
            >
              {t === 0 ? "0" : signedMan(t)}
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
              y={HEIGHT - 12}
              textAnchor="middle"
              fontSize={12}
              className="fill-carix-text/70"
            >
              {m / MONTHS_PER_YEAR}년
            </text>
          </g>
        ))}

        <polyline
          points={line(stay)}
          fill="none"
          strokeWidth={2}
          strokeLinejoin="round"
          className="stroke-carix-text/55"
        />
        <polyline
          points={line(move)}
          fill="none"
          strokeWidth={3}
          strokeLinejoin="round"
          className="stroke-carix-action"
        />
        <text
          x={x(months) - 4}
          y={y(stay[last]) + (stay[last] <= move[last] ? 16 : -8)}
          textAnchor="end"
          fontSize={12}
          fontWeight={600}
          className="fill-carix-text/70"
        >
          잔류
        </text>
        <text
          x={x(months) - 4}
          y={y(move[last]) + (move[last] < stay[last] ? 16 : -8)}
          textAnchor="end"
          fontSize={12}
          fontWeight={700}
          className="fill-carix-action"
        >
          이직
        </text>

        {crossAt !== null && (
          <g>
            <circle
              cx={x(crossAt)}
              cy={y(move[crossAt - 1])}
              r={7}
              strokeWidth={2.5}
              className="fill-carix-surface stroke-carix-action"
            />
            <text
              x={x(crossAt)}
              y={y(move[crossAt - 1]) - 14}
              textAnchor="middle"
              fontSize={13}
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
              cy={y(stay[pointer - 1])}
              r={4}
              className="fill-carix-text/70"
            />
            <circle
              cx={x(pointer)}
              cy={y(move[pointer - 1])}
              r={4}
              className="fill-carix-action"
            />
            <rect
              x={tipX}
              y={PAD.top + 18}
              width={TOOLTIP_W}
              height={TOOLTIP_H}
              rx={6}
              className="fill-carix-surface stroke-carix-border"
            />
            <text
              x={tipX + 10}
              y={PAD.top + 34}
              fontSize={11}
              className="fill-carix-text/70"
            >
              {pointer}개월째
            </text>
            <text
              x={tipX + 10}
              y={PAD.top + 51}
              fontSize={12}
              className="fill-carix-text"
            >
              잔류 {signedMan(stay[pointer - 1])} · 이직{" "}
              {signedMan(move[pointer - 1])}
            </text>
          </g>
        )}
      </svg>
      <figcaption className="mt-2 text-sm text-carix-text/70">
        {crossAt !== null
          ? `이직 누적이 ${crossAt}개월째에 잔류 누적을 따라잡습니다.`
          : "비교 기간 안에 이직 누적이 잔류 누적을 따라잡지 못합니다."}{" "}
        옅게 칠한 구간은 첫 12개월입니다.
      </figcaption>
    </figure>
  );
}
