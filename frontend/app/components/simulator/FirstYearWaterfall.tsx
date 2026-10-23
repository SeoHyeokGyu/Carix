import type { EvaluationResponse } from "../../lib/api/scenario";
import { amountLabel, niceTicks } from "./chartScale";

type FirstYear = NonNullable<EvaluationResponse["move"]>["firstYear"];

const WIDTH = 640;
const HEIGHT = 300;
const PAD = { left: 56, right: 16, top: 34, bottom: 44 };
const BAR_RATIO = 0.56;
const LABEL_CLEARANCE = 12;

interface Step {
  key: string;
  label: string;
  /** 막대의 시작·끝 누적값(원). */
  from: number;
  to: number;
  isTotal: boolean;
}

/** 0 에서 출발해 항목마다 누적하고, 마지막에 합계 막대를 0 에서 세운다. 실업급여 0 은 생략. */
export function waterfallSteps(firstYear: FirstYear): Step[] {
  const deltas = [
    { key: "salaryDiff", label: "연봉 차이", value: firstYear.salaryDiff },
    { key: "gap", label: "공백", value: firstYear.gap },
    { key: "oneTimeCost", label: "일회성 비용", value: firstYear.oneTimeCost },
    { key: "benefit", label: "실업급여", value: firstYear.benefit },
  ].filter((d) => d.key !== "benefit" || d.value !== 0);

  let running = 0;
  const steps: Step[] = deltas.map((d) => {
    const from = running;
    running += d.value;
    return { key: d.key, label: d.label, from, to: running, isTotal: false };
  });
  return [
    ...steps,
    {
      key: "total",
      label: "합계",
      from: 0,
      to: firstYear.total,
      isTotal: true,
    },
  ];
}

export function FirstYearWaterfall({ firstYear }: { firstYear: FirstYear }) {
  const steps = waterfallSteps(firstYear);
  const values = steps.flatMap((s) => [s.from, s.to]).concat(0);
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const pad = (hi - lo || 1) * 0.08;
  const ticks = niceTicks(lo - pad, hi + pad, 4);
  const yMin = Math.min(lo - pad, ticks[0]);
  const yMax = Math.max(hi + pad, ticks[ticks.length - 1]);

  const plotW = WIDTH - PAD.left - PAD.right;
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const y = (v: number) =>
    PAD.top + plotH - ((v - yMin) / (yMax - yMin)) * plotH;
  const slot = plotW / steps.length;
  const barW = slot * BAR_RATIO;
  const x = (i: number) => PAD.left + slot * i + (slot - barW) / 2;

  const parts = steps
    .filter((s) => !s.isTotal)
    .map((s) => `${s.label} ${amountLabel(s.to - s.from)}`)
    .join(", ");
  const ariaLabel = `새 회사 첫해 12개월 누적 차이 계단 그림. 지금 회사를 0 으로 놓고 ${parts}, 합계 ${amountLabel(firstYear.total)}원.`;
  const verdict =
    firstYear.total >= 0
      ? "첫해에 새 회사가 지금 회사보다 앞섭니다"
      : "첫해에는 지금 회사가 앞섭니다";

  return (
    <figure className="m-0">
      <svg
        role="img"
        aria-label={ariaLabel}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="h-auto w-full"
      >
        {ticks.map((t) => (
          <g key={t}>
            <line
              x1={PAD.left}
              x2={WIDTH - PAD.right}
              y1={y(t)}
              y2={y(t)}
              className="stroke-carix-border"
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
              {t === 0 ? "지금 회사 = 0" : amountLabel(t)}
            </text>
          </g>
        ))}
        <line
          x1={PAD.left}
          x2={WIDTH - PAD.right}
          y1={y(0)}
          y2={y(0)}
          className="stroke-carix-text/70"
          strokeWidth={1.5}
        />

        {steps.map((s, i) => {
          const top = y(Math.max(s.from, s.to));
          const bottom = y(Math.min(s.from, s.to));
          const delta = s.to - s.from;
          const isGain = delta >= 0;
          const fillClass = s.isTotal
            ? isGain
              ? "fill-carix-positive"
              : "fill-carix-danger"
            : isGain
              ? "fill-carix-positive/45"
              : "fill-carix-danger/45";
          const baseY = y(0);
          const rawLabelY = isGain ? top - 7 : bottom + 15;
          // 기준선 가까이 걸친 라벨은 선 반대쪽으로 비켜 선과 겹치지 않게 한다.
          const labelY =
            Math.abs(rawLabelY - 4 - baseY) < LABEL_CLEARANCE
              ? isGain
                ? baseY - 6
                : baseY + 15
              : rawLabelY;
          const next = steps[i + 1];
          return (
            <g key={s.key}>
              {next && (
                <line
                  x1={x(i) + barW}
                  x2={x(i + 1)}
                  y1={y(next.isTotal ? firstYear.total : s.to)}
                  y2={y(next.isTotal ? firstYear.total : s.to)}
                  className="stroke-carix-text/35"
                  strokeDasharray="3 3"
                />
              )}
              <rect
                x={x(i)}
                y={top}
                width={barW}
                height={Math.max(bottom - top, 1.5)}
                rx={3}
                className={fillClass}
              />
              <text
                x={x(i) + barW / 2}
                y={labelY}
                textAnchor="middle"
                fontSize={s.isTotal ? 14 : 12}
                fontWeight={s.isTotal ? 700 : 600}
                className="fill-carix-text"
              >
                {amountLabel(delta)}
              </text>
              <text
                x={x(i) + barW / 2}
                y={HEIGHT - PAD.bottom + 22}
                textAnchor="middle"
                fontSize={12}
                fontWeight={s.isTotal ? 700 : 400}
                className="fill-carix-text/80"
              >
                {s.label}
              </text>
            </g>
          );
        })}
      </svg>
      <figcaption className="mt-2 text-sm text-carix-text/70">
        {verdict} — 합계 {amountLabel(firstYear.total)}원. 지금 회사 첫해를 0
        으로 놓고 항목별로 더하고 뺀 값입니다.
      </figcaption>
    </figure>
  );
}
