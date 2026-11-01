import { useState } from "react";

import { Card, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ToneBadge } from "@/components/ui/tone-badge";

import { useElementWidth } from "../../hooks/useElementWidth";
import type { IndexStatus } from "../../lib/api/dashboard";
import type {
  TrendListResponse,
  TrendPoint,
} from "../../lib/dashboardMarketApi";
import { resolveIndexStatus } from "../../lib/indexStatus";

// 차트는 실제 폭에 1:1 로 그린다 — 고정 viewBox 를 늘리면 넓은 화면에서 높이·글자가 같이 부푼다.
const FALLBACK_WIDTH = 640;
const HEIGHT = 280;
const FONT = 12;
// 월 라벨(「26.01」) 하나가 차지하는 폭. 이보다 촘촘하면 최근 달부터 건너뛰며 찍는다.
const MIN_LABEL_SPACING = 44;
// 이보다 좁으면 오른쪽 「기준 (100)」 라벨을 빼고 그 자리를 그래프에 준다 — Y축의 굵은 100 이 같은 말을 한다.
const BASELINE_LABEL_MIN_WIDTH = 480;

export interface IncomeVsPriceChartProps {
  data: TrendListResponse;
}

/**
 * YYYYMM 또는 YYYY-MM 형식을 "YY.MM" 형태로 포맷
 */
function formatYearMonth(ym: string): string {
  const clean = ym.replace("-", "");
  if (clean.length === 6) {
    return `${clean.slice(2, 4)}.${clean.slice(4, 6)}`;
  }
  return ym;
}

/**
 * 원 단위 금액을 "X,XXX만 원" 형태로 포맷
 */
function formatWon(amount: number | null | undefined): string {
  if (amount == null) return "-";
  const man = Math.round(amount / 10_000);
  return `${man.toLocaleString()}만 원`;
}

export function IncomeVsPriceChart({ data }: IncomeVsPriceChartProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [chartRef, width] = useElementWidth<HTMLDivElement>(FALLBACK_WIDTH);

  const { trends, purchasingPowerStatus } = data;
  // 지표 카드와 같은 해석 모듈이 문구·톤을 정한다. 와이어 타입은 string 이고, 모르는 코드는
  // 해석 모듈이 코드 그대로 muted 로 돌려준다.
  const status = purchasingPowerStatus
    ? resolveIndexStatus(purchasingPowerStatus as IndexStatus)
    : null;

  // 유효한 데이터 포인트 판별 (points가 없거나 모두 null인 경우 빈 상태)
  const validPoints = (trends ?? []).filter(
    (p) =>
      p.incomeIndex != null ||
      p.personalPriceIndex != null ||
      p.purchasingPowerIndex != null,
  );

  // 1. 비어있음 상태 또는 12개월 미만 안내 (기능 명세 §5, US-D3)
  if (!trends || trends.length === 0 || validPoints.length === 0) {
    return (
      <EmptyState
        data-testid="trends-empty-state"
        icon="📊"
        title="시계열 추이 데이터가 아직 충분하지 않습니다."
        description="다음 달부터 지수 변화를 확인하실 수 있습니다. 최소 2개월 이상의 스냅샷 데이터가 누적되면 추이 그래프가 제공됩니다."
      />
    );
  }

  // 12개월 미만 데이터 안내
  const isLessThan12Months = trends.length < 12;

  // 데이터 모드 판별: incomeIndex & personalPriceIndex가 있는지, 아니면 purchasingPowerIndex만 있는지
  const hasIncomeAndPrice = trends.some(
    (p) => p.incomeIndex != null && p.personalPriceIndex != null,
  );

  const height = HEIGHT;
  const paddingLeft = 40;
  const showsBaselineLabel = width >= BASELINE_LABEL_MIN_WIDTH;
  const paddingRight = showsBaselineLabel ? 64 : 16;
  const paddingTop = 30;
  const paddingBottom = 40;

  const chartWidth = width - paddingLeft - paddingRight;
  const chartHeight = height - paddingTop - paddingBottom;

  // Y축 스케일 계산 (기준선 100을 항상 포함)
  const allValues: number[] = [100];
  trends.forEach((p) => {
    if (hasIncomeAndPrice) {
      if (p.incomeIndex != null) allValues.push(p.incomeIndex);
      if (p.personalPriceIndex != null) allValues.push(p.personalPriceIndex);
    } else {
      if (p.purchasingPowerIndex != null)
        allValues.push(p.purchasingPowerIndex);
      if (p.marketValueIndex != null) allValues.push(p.marketValueIndex);
    }
  });

  const rawMin = Math.min(...allValues);
  const rawMax = Math.max(...allValues);
  const yMin = Math.floor(Math.min(80, rawMin - 5) / 10) * 10;
  const yMax = Math.ceil(Math.max(120, rawMax + 5) / 10) * 10;
  const yRange = yMax - yMin || 1;

  const getY = (val: number) => {
    return paddingTop + chartHeight - ((val - yMin) / yRange) * chartHeight;
  };

  const getX = (index: number) => {
    if (trends.length <= 1) return paddingLeft + chartWidth / 2;
    return paddingLeft + (index / (trends.length - 1)) * chartWidth;
  };

  const baselineY = getY(100);

  const spacing =
    trends.length > 1 ? chartWidth / (trends.length - 1) : chartWidth;
  const labelStep = Math.max(1, Math.ceil(MIN_LABEL_SPACING / spacing));
  const showsLabel = (i: number) => (trends.length - 1 - i) % labelStep === 0;

  // 선 세그먼트 생성 (결측치 갭 처리: null이 있으면 선을 잇지 않고 분리)
  const createLineSegments = (
    getValue: (p: TrendPoint) => number | null,
  ): string[] => {
    const segments: string[] = [];
    let currentSegment: string[] = [];

    trends.forEach((p, i) => {
      const val = getValue(p);
      if (val != null) {
        const x = getX(i);
        const y = getY(val);
        if (currentSegment.length === 0) {
          currentSegment.push(`M ${x.toFixed(1)} ${y.toFixed(1)}`);
        } else {
          currentSegment.push(`L ${x.toFixed(1)} ${y.toFixed(1)}`);
        }
      } else {
        if (currentSegment.length > 0) {
          segments.push(currentSegment.join(" "));
          currentSegment = [];
        }
      }
    });

    if (currentSegment.length > 0) {
      segments.push(currentSegment.join(" "));
    }
    return segments;
  };

  // 구매력 손실 구간 (소득 지수가 개인물가 지수 아래인 구간 음영)
  // hasIncomeAndPrice 모드일 때 인접한 두 점 사이의 영역 다각형 생성
  const lossPolygons: string[] = [];
  if (hasIncomeAndPrice) {
    for (let i = 0; i < trends.length - 1; i++) {
      const p1 = trends[i];
      const p2 = trends[i + 1];
      if (
        p1.incomeIndex != null &&
        p1.personalPriceIndex != null &&
        p2.incomeIndex != null &&
        p2.personalPriceIndex != null
      ) {
        // 둘 다 소득선이 물가선 아래인 경우
        if (
          p1.incomeIndex <= p1.personalPriceIndex &&
          p2.incomeIndex <= p2.personalPriceIndex
        ) {
          const x1 = getX(i).toFixed(1);
          const x2 = getX(i + 1).toFixed(1);
          const y1Income = getY(p1.incomeIndex).toFixed(1);
          const y2Income = getY(p2.incomeIndex).toFixed(1);
          const y1Price = getY(p1.personalPriceIndex).toFixed(1);
          const y2Price = getY(p2.personalPriceIndex).toFixed(1);

          lossPolygons.push(
            `M ${x1} ${y1Price} L ${x2} ${y2Price} L ${x2} ${y2Income} L ${x1} ${y1Income} Z`,
          );
        }
      }
    }
  }

  const incomeSegments = createLineSegments((p) =>
    hasIncomeAndPrice ? p.incomeIndex : p.purchasingPowerIndex,
  );
  const priceSegments = hasIncomeAndPrice
    ? createLineSegments((p) => p.personalPriceIndex)
    : createLineSegments((p) => p.marketValueIndex);

  // Y축 틱 계산
  const yTicks: number[] = [];
  for (let val = yMin; val <= yMax; val += 10) {
    yTicks.push(val);
  }

  const activePoint =
    hoveredIndex != null && trends[hoveredIndex] ? trends[hoveredIndex] : null;

  return (
    <Card data-testid="income-vs-price-chart">
      {/* 1. 상단 타이틀 및 상태 */}
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-carix-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <CardTitle>
              {hasIncomeAndPrice
                ? "소득 대 개인물가 지수 추이"
                : "구매력 및 시장가치 지수 추이"}
            </CardTitle>
            {status && <ToneBadge tone={status.tone}>{status.label}</ToneBadge>}
          </div>
          <p className="mt-1 text-xs text-carix-text/70">
            기준월 지수(100) 대비 실질 구매력 변동 및 물가 압력 추이를
            분석합니다.
          </p>
        </div>

        {/* 범례 */}
        <div className="flex flex-wrap items-center gap-3 text-xs">
          <div className="flex items-center gap-1.5">
            <span className="inline-block h-0.5 w-4 bg-carix-info rounded" />
            <span className="font-medium text-carix-text">
              {hasIncomeAndPrice ? "소득 지수 (실선)" : "구매력 지수"}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="inline-block h-0.5 w-4 border-t-2 border-dashed border-carix-warning" />
            <span className="font-medium text-carix-text">
              {hasIncomeAndPrice ? "개인물가 지수 (점선)" : "시장가치 지수"}
            </span>
          </div>
          {hasIncomeAndPrice && (
            <div className="flex items-center gap-1.5">
              <span className="inline-block h-3 w-3 rounded bg-carix-danger/20 border border-carix-danger/40" />
              <span className="text-carix-danger font-medium">
                구매력 손실 구간
              </span>
            </div>
          )}
        </div>
      </div>

      {/* 12개월 미만 알림 배너 */}
      {isLessThan12Months && (
        <div className="rounded-lg bg-carix-bg/80 border border-carix-border/80 px-3.5 py-2 text-xs text-carix-text/70 flex items-center gap-2">
          <span>ℹ️</span>
          <span>
            현재 <strong>{trends.length}개월</strong> 데이터가 누적되어
            있습니다. 12개월 전체 사이클 추이는 스냅샷이 누적됨에 따라 자동으로
            확장됩니다.
          </span>
        </div>
      )}

      {/* 2. SVG 시계열 라인 차트 */}
      <div ref={chartRef} className="relative my-2 w-full overflow-hidden">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          width={width}
          height={height}
          className="block w-full overflow-visible select-none"
          role="img"
          aria-label="소득 대 개인물가 지수 시계열 차트"
        >
          {/* Y축 그리드선 & 틱 라벨 */}
          {yTicks.map((val) => {
            const y = getY(val);
            const isBaseline = val === 100;
            return (
              <g key={val}>
                <line
                  x1={paddingLeft}
                  y1={y}
                  x2={width - paddingRight}
                  y2={y}
                  stroke={
                    isBaseline
                      ? "var(--color-carix-text)"
                      : "var(--color-carix-border)"
                  }
                  strokeWidth={isBaseline ? "1.5" : "1"}
                  strokeDasharray={isBaseline ? "4 3" : "2 2"}
                  opacity={isBaseline ? 0.35 : 0.3}
                />
                <text
                  x={paddingLeft - 8}
                  y={y + 3.5}
                  textAnchor="end"
                  fontSize={FONT}
                  fill="var(--color-carix-text)"
                  opacity={isBaseline ? 0.9 : 0.5}
                  fontWeight={isBaseline ? "bold" : "normal"}
                >
                  {val}
                </text>
              </g>
            );
          })}

          {/* 기준선(100) 기준 라벨 */}
          {showsBaselineLabel && (
            <text
              x={width - paddingRight + 4}
              y={baselineY + 3}
              fontSize={FONT - 1}
              fill="var(--color-carix-text)"
              opacity={0.6}
            >
              기준 (100)
            </text>
          )}

          {/* 구매력 손실 구간 음영 (소득 < 물가) */}
          {lossPolygons.map((poly, idx) => (
            <path
              key={idx}
              d={poly}
              fill="var(--color-carix-danger)"
              opacity="0.18"
            />
          ))}

          {/* X축 그리드선 & 라벨 */}
          {trends.map((p, i) => {
            const x = getX(i);
            const isHovered = hoveredIndex === i;
            return (
              <g key={p.yearMonth}>
                <line
                  x1={x}
                  y1={paddingTop}
                  x2={x}
                  y2={height - paddingBottom}
                  stroke="var(--color-carix-border)"
                  strokeWidth={isHovered ? "1.5" : "1"}
                  strokeDasharray="2 2"
                  opacity={isHovered ? 0.6 : 0.2}
                />
                {showsLabel(i) && (
                  <text
                    x={x}
                    y={height - paddingBottom + 18}
                    textAnchor="middle"
                    fontSize={FONT}
                    fill="var(--color-carix-text)"
                    opacity={isHovered ? 0.95 : 0.6}
                    fontWeight={isHovered ? "bold" : "normal"}
                  >
                    {formatYearMonth(p.yearMonth)}
                  </text>
                )}
              </g>
            );
          })}

          {/* 물가선 (또는 시장가치선) - Amber 점선 */}
          {priceSegments.map((d, i) => (
            <path
              key={`price-seg-${i}`}
              d={d}
              fill="none"
              stroke="var(--color-carix-warning)"
              strokeWidth="2.5"
              strokeDasharray="4 3"
              strokeLinecap="round"
            />
          ))}

          {/* 소득선 (또는 구매력선) - Indigo 실선 */}
          {incomeSegments.map((d, i) => (
            <path
              key={`income-seg-${i}`}
              d={d}
              fill="none"
              stroke="var(--color-carix-info)"
              strokeWidth="2.5"
              strokeLinecap="round"
            />
          ))}

          {/* 데이터 포인트 점들 및 인터랙션 타겟 */}
          {trends.map((p, i) => {
            const x = getX(i);
            const incVal = hasIncomeAndPrice
              ? p.incomeIndex
              : p.purchasingPowerIndex;
            const priceVal = hasIncomeAndPrice
              ? p.personalPriceIndex
              : p.marketValueIndex;
            const isHovered = hoveredIndex === i;

            return (
              <g
                key={`point-${p.yearMonth}`}
                className="cursor-pointer"
                onMouseEnter={() => setHoveredIndex(i)}
                onMouseLeave={() => setHoveredIndex(null)}
              >
                {/* 물가 점 */}
                {priceVal != null && (
                  <circle
                    cx={x}
                    cy={getY(priceVal)}
                    r={isHovered ? 5 : 3.5}
                    fill="var(--color-carix-warning)"
                    stroke="var(--carix-surface)"
                    strokeWidth="1.5"
                  />
                )}
                {/* 소득 점 */}
                {incVal != null && (
                  <circle
                    cx={x}
                    cy={getY(incVal)}
                    r={isHovered ? 5 : 3.5}
                    fill="var(--color-carix-info)"
                    stroke="var(--carix-surface)"
                    strokeWidth="1.5"
                  />
                )}
                {/* 투명 마우스 호버 영역 */}
                <rect
                  x={x - 15}
                  y={paddingTop}
                  width={30}
                  height={chartHeight}
                  fill="transparent"
                />
              </g>
            );
          })}
        </svg>

        {/* 인터랙티브 플로팅 툴팁 */}
        {activePoint && hoveredIndex != null && (
          <div
            className="pointer-events-none absolute -top-2 rounded-lg border border-carix-border bg-carix-surface/95 p-3 text-xs shadow-lg backdrop-blur-sm transition-all duration-150 z-20"
            style={{
              left: `${Math.min(75, Math.max(10, (hoveredIndex / (trends.length - 1 || 1)) * 100))}%`,
              transform: "translateX(-50%)",
            }}
          >
            <div className="font-bold text-carix-text border-b border-carix-border pb-1 mb-1.5 flex items-center justify-between gap-3">
              <span>{activePoint.yearMonth}</span>
              <span className="text-[10px] text-carix-text/60">
                기준월 대비
              </span>
            </div>
            <div className="space-y-1">
              <div className="flex items-center justify-between gap-4">
                <span className="flex items-center gap-1 text-carix-info font-medium">
                  <span className="h-2 w-2 rounded-full bg-carix-info inline-block" />
                  {hasIncomeAndPrice ? "소득 지수" : "구매력 지수"}:
                </span>
                <strong className="text-carix-text">
                  {(hasIncomeAndPrice
                    ? activePoint.incomeIndex
                    : activePoint.purchasingPowerIndex) ?? "결측"}
                </strong>
              </div>
              <div className="flex items-center justify-between gap-4">
                <span className="flex items-center gap-1 text-carix-warning font-medium">
                  <span className="h-2 w-2 rounded-full bg-carix-warning inline-block" />
                  {hasIncomeAndPrice ? "개인물가 지수" : "시장가치 지수"}:
                </span>
                <strong className="text-carix-text">
                  {(hasIncomeAndPrice
                    ? activePoint.personalPriceIndex
                    : activePoint.marketValueIndex) ?? "결측"}
                </strong>
              </div>

              {/* 보조 원화 정보 (툴팁에만 표기) */}
              {(activePoint.nominalIncome != null ||
                activePoint.realIncome != null) && (
                <div className="mt-2 border-t border-carix-border/60 pt-1.5 text-[11px] text-carix-text/70 space-y-0.5">
                  {activePoint.nominalIncome != null && (
                    <div className="flex justify-between gap-2">
                      <span>명목 소득:</span>
                      <span>{formatWon(activePoint.nominalIncome)}</span>
                    </div>
                  )}
                  {activePoint.realIncome != null && (
                    <div className="flex justify-between gap-2">
                      <span>실질 소득:</span>
                      <span>{formatWon(activePoint.realIncome)}</span>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 3. 하단 안내 및 메타데이터 */}
      <div className="rounded-lg bg-carix-bg/40 p-3 text-xs text-carix-text/70 border border-carix-border/60 space-y-1">
        {/* 제목을 본문과 한 흐름으로 둔다 — flex 로 나란히 두면 좁은 화면에서 제목 칸이 글자 단위로 쪼개진다. */}
        <p>
          <span className="font-semibold text-carix-text">
            💡 차트 해석 가이드:
          </span>{" "}
          소득 지수가 개인물가 지수보다 아래에 머무르는 구간은 물가 상승률에
          소득 증가율이 미치지 못해 <strong>실질 구매력 손실</strong>이 발생하는
          시기입니다.
        </p>
        <p className="text-[11px] text-carix-text/50">
          * 지수는 기준월을 100으로 산정한 상대 지표이며, 금액 축 혼용을
          방지하기 위해 원화 금액은 툴팁 보조 정보로만 확인하실 수 있습니다.
        </p>
      </div>
    </Card>
  );
}
