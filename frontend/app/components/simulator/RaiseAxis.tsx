const WIDTH = 640;
const HEIGHT = 190;
const PAD_X = 28;
const BAND_TOP = 70;
const BAND_H = 34;
const DOMAIN_MARGIN = 2;
const LABEL_FONT = 12;

export interface RaiseAxisProps {
  expectedPercent: number;
  /** 개인물가 기준 손익분기 인상률(%). 계산 못 하면 null. */
  breakevenPersonal: number | null;
  breakevenGovernment: number | null;
  /** 새 회사 제안이 현 연봉 대비 몇 %인지. */
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
  /** 라벨이 그림 가장자리를 넘으면 앵커를 뒤집거나 안쪽으로 민다. 글자 폭은 어림이다. */
  const fit = (
    px: number,
    text: string,
    preferred: "start" | "middle" | "end",
    gap = 0,
  ) => {
    const width = [...text].reduce(
      (sum, ch) => sum + (ch.charCodeAt(0) > 127 ? 1 : 0.6) * LABEL_FONT,
      0,
    );
    const offset = preferred === "end" ? -gap : preferred === "start" ? gap : 0;
    const left =
      preferred === "end"
        ? px + offset - width
        : preferred === "middle"
          ? px - width / 2
          : px + offset;
    if (preferred === "end" && left < PAD_X) {
      return { x: px + gap, anchor: "start" as const };
    }
    if (preferred === "start" && left + width > WIDTH - PAD_X) {
      return { x: px - gap, anchor: "end" as const };
    }
    if (preferred === "middle") {
      if (left < PAD_X) return { x: PAD_X, anchor: "start" as const };
      if (left + width > WIDTH - PAD_X) {
        return { x: WIDTH - PAD_X, anchor: "end" as const };
      }
    }
    return { x: px + offset, anchor: preferred };
  };
  // 손익분기 선 둘의 이름이 겹치지 않게 왼쪽 선 이름은 왼쪽으로, 오른쪽 선 이름은 오른쪽으로 편다.
  const breakevenLabel = (value: number, other: number | null, text: string) =>
    fit(
      x(value),
      text,
      other === null || other === value
        ? "middle"
        : value < other
          ? "end"
          : "start",
      6,
    );
  const personalText =
    breakevenPersonal !== null ? `개인물가 기준 ${pct(breakevenPersonal)}` : "";
  const governmentText =
    breakevenGovernment !== null
      ? `정부 전망 기준 ${pct(breakevenGovernment)}`
      : "";
  const personalLabel =
    breakevenPersonal !== null
      ? breakevenLabel(breakevenPersonal, breakevenGovernment, personalText)
      : null;
  const governmentLabel =
    breakevenGovernment !== null
      ? breakevenLabel(breakevenGovernment, breakevenPersonal, governmentText)
      : null;
  const offerText =
    offerEquivalentPercent != null
      ? `새 회사 제안 ${pct(offerEquivalentPercent)}`
      : "";
  const offerLabel =
    offerEquivalentPercent != null
      ? fit(x(offerEquivalentPercent), offerText, "middle")
      : null;
  const expectedText = `기대 인상률 ${pct(expectedPercent)}`;
  const expectedLabel = fit(x(expectedPercent), expectedText, "middle");

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
      ? `새 회사 제안은 현 연봉 대비 ${pct(offerEquivalentPercent)}`
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
        aria-label={`지금 회사 인상률 축. ${facts.join(", ")}.`}
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
              display={
                splitX - PAD_X < 70 ||
                Math.abs(x(expectedPercent) - (splitX - 28)) < 24
                  ? "none"
                  : undefined
              }
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
              display={
                PAD_X + plotW - splitX < 70 ||
                Math.abs(x(expectedPercent) - (splitX + 22)) < 24
                  ? "none"
                  : undefined
              }
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
              x={personalLabel?.x}
              y={bandBottom + 40}
              textAnchor={personalLabel?.anchor}
              fontSize={12}
              className="fill-carix-text"
            >
              {personalText}
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
              x={governmentLabel?.x}
              y={bandBottom + (showGovernmentDashed ? 62 : 40)}
              textAnchor={governmentLabel?.anchor}
              fontSize={12}
              className="fill-carix-text/80"
            >
              {governmentText}
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
              x={offerLabel?.x}
              y={BAND_TOP - 40}
              textAnchor={offerLabel?.anchor}
              fontSize={12}
              fontWeight={600}
              className="fill-carix-info"
            >
              {offerText}
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
            x={expectedLabel.x}
            y={BAND_TOP - 20}
            textAnchor={expectedLabel.anchor}
            fontSize={13}
            fontWeight={700}
            className="fill-carix-action"
          >
            {expectedText}
          </text>
        </g>
      </svg>
      <figcaption className="mt-2 text-sm text-carix-text/70">
        {caption}
      </figcaption>
    </figure>
  );
}
