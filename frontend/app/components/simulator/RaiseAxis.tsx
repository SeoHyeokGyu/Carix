const WIDTH = 640;
const HEIGHT = 190;
const PAD_X = 28;
const BAND_TOP = 70;
const BAND_H = 34;
const DOMAIN_MARGIN = 2;

export interface RaiseAxisProps {
  expectedPercent: number;
  /** 개인물가 기준 손익분기 인상률(%). 계산 못 하면 null. */
  breakevenPersonal: number | null;
  breakevenGovernment: number | null;
  /** 이직 제안이 현 연봉 대비 몇 %인지. */
  offerEquivalentPercent?: number | null;
}

function pct(value: number): string {
  return `${value.toFixed(1)}%`;
}

export function RaiseAxis({
  expectedPercent,
  breakevenPersonal,
  breakevenGovernment,
  offerEquivalentPercent = null,
}: RaiseAxisProps) {
  const values = [
    expectedPercent,
    breakevenPersonal,
    breakevenGovernment,
    offerEquivalentPercent,
  ].filter((v): v is number => v !== null);
  const min = Math.min(...values) - DOMAIN_MARGIN;
  const max = Math.max(...values) + DOMAIN_MARGIN;
  const plotW = WIDTH - PAD_X * 2;
  const x = (v: number) => PAD_X + ((v - min) / (max - min)) * plotW;
  const anchorFor = (px: number) =>
    px < 90 ? "start" : px > WIDTH - 90 ? "end" : "middle";
  // 손익분기 선 둘의 이름이 겹치지 않게 왼쪽 선 이름은 왼쪽으로, 오른쪽 선 이름은 오른쪽으로 편다.
  const labelX = (value: number, anchor: string) =>
    x(value) + (anchor === "end" ? -6 : anchor === "start" ? 6 : 0);
  const breakevenAnchor = (value: number, other: number | null) => {
    if (other === null || other === value) return anchorFor(x(value));
    return value < other ? "end" : "start";
  };

  // 손익분기가 둘이면 개인 기준으로 손해·이득을 가르고, 개인 기준이 없으면 정부 기준 하나로 가른다.
  const split = breakevenPersonal ?? breakevenGovernment;
  const splitX = split === null ? null : x(split);
  const showGovernmentDashed =
    breakevenPersonal !== null && breakevenGovernment !== null;
  const bandBottom = BAND_TOP + BAND_H;

  const facts = [
    `기대 인상률 ${pct(expectedPercent)}`,
    breakevenPersonal !== null
      ? `개인물가 기준 손익분기 ${pct(breakevenPersonal)}`
      : null,
    breakevenGovernment !== null
      ? `정부 전망 기준 손익분기 ${pct(breakevenGovernment)}`
      : null,
    offerEquivalentPercent != null
      ? `이직 제안은 현 연봉 대비 ${pct(offerEquivalentPercent)}`
      : null,
  ].filter((f): f is string => f !== null);

  const aboveBreakeven = split !== null && expectedPercent >= split;
  const caption =
    split === null
      ? "손익분기 인상률을 계산할 수 없어 기대 인상률만 보입니다."
      : aboveBreakeven
        ? `기대 인상률 ${pct(expectedPercent)}가 손익분기 ${pct(split)}를 넘어 실질 소득이 늘어납니다.`
        : `기대 인상률 ${pct(expectedPercent)}가 손익분기 ${pct(split)}에 못 미쳐 실질 소득이 줄어듭니다.`;

  return (
    <figure className="m-0">
      <svg
        role="img"
        aria-label={`잔류 시 인상률 축. ${facts.join(", ")}.`}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="h-auto w-full"
      >
        <rect
          x={PAD_X}
          y={BAND_TOP}
          width={(splitX ?? PAD_X + plotW) - PAD_X}
          height={BAND_H}
          rx={4}
          className={
            split === null ? "fill-carix-border" : "fill-carix-danger/25"
          }
        />
        {splitX !== null && (
          <rect
            x={splitX}
            y={BAND_TOP}
            width={PAD_X + plotW - splitX}
            height={BAND_H}
            rx={4}
            className="fill-carix-positive/25"
          />
        )}
        {splitX !== null && (
          <>
            <text
              x={Math.max(PAD_X + 8, splitX - 10)}
              y={BAND_TOP + BAND_H / 2}
              textAnchor="end"
              dominantBaseline="central"
              fontSize={12}
              fontWeight={600}
              className="fill-carix-danger"
              display={splitX - PAD_X < 70 ? "none" : undefined}
            >
              손해
            </text>
            <text
              x={Math.min(WIDTH - PAD_X - 8, splitX + 10)}
              y={BAND_TOP + BAND_H / 2}
              dominantBaseline="central"
              fontSize={12}
              fontWeight={600}
              className="fill-carix-positive"
              display={PAD_X + plotW - splitX < 70 ? "none" : undefined}
            >
              이득
            </text>
          </>
        )}

        {breakevenPersonal !== null && (
          <g>
            <line
              x1={x(breakevenPersonal)}
              x2={x(breakevenPersonal)}
              y1={BAND_TOP - 6}
              y2={bandBottom + 26}
              strokeWidth={2.5}
              className="stroke-carix-text"
            />
            <text
              x={labelX(
                breakevenPersonal,
                breakevenAnchor(breakevenPersonal, breakevenGovernment),
              )}
              y={bandBottom + 40}
              textAnchor={breakevenAnchor(
                breakevenPersonal,
                breakevenGovernment,
              )}
              fontSize={12}
              className="fill-carix-text"
            >
              개인물가 기준 {pct(breakevenPersonal)}
            </text>
          </g>
        )}
        {breakevenGovernment !== null && (
          <g>
            <line
              x1={x(breakevenGovernment)}
              x2={x(breakevenGovernment)}
              y1={BAND_TOP - 6}
              y2={bandBottom + (showGovernmentDashed ? 66 : 26)}
              strokeWidth={showGovernmentDashed ? 2 : 2.5}
              strokeDasharray={showGovernmentDashed ? "5 4" : undefined}
              className="stroke-carix-text"
            />
            <text
              x={labelX(
                breakevenGovernment,
                breakevenAnchor(breakevenGovernment, breakevenPersonal),
              )}
              y={bandBottom + (showGovernmentDashed ? 62 : 40)}
              textAnchor={breakevenAnchor(
                breakevenGovernment,
                breakevenPersonal,
              )}
              fontSize={12}
              className="fill-carix-text/80"
            >
              정부 전망 기준 {pct(breakevenGovernment)}
            </text>
          </g>
        )}

        {offerEquivalentPercent != null && (
          <g>
            <path
              d={`M ${x(offerEquivalentPercent)} ${BAND_TOP - 4} l -6 -9 h 12 z`}
              className="fill-carix-info"
            />
            <text
              x={x(offerEquivalentPercent)}
              y={BAND_TOP - 40}
              textAnchor={anchorFor(x(offerEquivalentPercent))}
              fontSize={12}
              fontWeight={600}
              className="fill-carix-info"
            >
              이직 제안 {pct(offerEquivalentPercent)}
            </text>
          </g>
        )}

        <g>
          <circle
            cx={x(expectedPercent)}
            cy={BAND_TOP + BAND_H / 2}
            r={9}
            strokeWidth={3}
            className="fill-carix-action stroke-carix-surface"
          />
          <text
            x={x(expectedPercent)}
            y={BAND_TOP - 20}
            textAnchor={anchorFor(x(expectedPercent))}
            fontSize={13}
            fontWeight={700}
            className="fill-carix-action"
          >
            기대 인상률 {pct(expectedPercent)}
          </text>
        </g>
      </svg>
      <figcaption className="mt-2 text-sm text-carix-text/70">
        {caption}
      </figcaption>
    </figure>
  );
}
