import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { HEADING } from "@/components/ui/heading";
import { ToneBadge } from "@/components/ui/tone-badge";
import { cn } from "@/lib/utils";

import {
  fetchSalaryDistribution,
  type SalaryDistributionResponse,
} from "../../lib/dashboardMarketApi";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import {
  bracketLabel,
  formatAppliedConditions,
  wageBasisLabel,
  wageDefinitionLabel,
  wageSourceLabel,
} from "../../lib/sourceLabel";
import { SalaryTrend } from "./SalaryTrend";

export { formatKoreanSalary };

export interface SalaryDistributionChartProps {
  data: SalaryDistributionResponse;
  onYearChange?: (year: string) => void;
}

interface BodyPartInfo {
  name: string;
  icon: string;
  label: string;
  desc: string;
}

export type SalaryBand = "head" | "shoulder" | "knee" | "ankle";

/**
 * 공고 연봉 4분위 구간. 구간 카드 금액이 p25·중앙값·p75 로 나뉘므로 문구·강조·헤드라인도 이 경계 하나를 쓴다.
 * 허리는 구간이 아니라 중앙값 선이다.
 */
export function salaryBand(percentile: number): SalaryBand {
  if (percentile >= 75) return "head";
  if (percentile >= 50) return "shoulder";
  if (percentile >= 25) return "knee";
  return "ankle";
}

const BODY_PARTS: Record<SalaryBand, BodyPartInfo> = {
  head: {
    name: "머리",
    icon: "🧠",
    label: "상위 머리",
    desc: "동료집단 상위 25% 안의 연봉 구간입니다.",
  },
  shoulder: {
    name: "어깨",
    icon: "👔",
    label: "든든한 어깨",
    desc: "동료집단 중앙값을 넘어선 상위 25~50% 구간입니다.",
  },
  knee: {
    name: "무릎",
    icon: "🦵",
    label: "도약의 무릎",
    desc: "중앙값 바로 아래, 하위 25~50% 구간입니다.",
  },
  ankle: {
    name: "발목",
    icon: "👟",
    label: "첫걸음 발목",
    desc: "동료집단 하위 25% 안의 연봉 구간입니다.",
  },
};

/** 백분위(0~100)가 속한 4분위 구간의 신체 부위. */
export function getBodyPartInfo(percentile: number): BodyPartInfo {
  return BODY_PARTS[salaryBand(percentile)];
}

// 실루엣 상자 높이. 상자 style 과 이름표 이동량(% → px)이 같은 값을 쓴다.
const FIGURE_HEIGHT_PX = 256;
// 두 표시가 이보다 가까우면(그래픽 높이 %) 이름표끼리 겹친다.
export const MARKER_LABEL_GAP = 8;

/**
 * 이름표를 옆 표시와 겹치지 않게 옮길 양(그래픽 높이 %, 양수는 아래로). 선은 제자리에 둔다.
 * 내 연봉 5,000만·공식 기준 4,769만처럼 가까우면 「공식기준」 이 내 연봉 선 끝에 붙어 그 선의 이름처럼 읽혔다.
 */
export function labelNudge(own: number, other: number | null): number {
  if (other == null) return 0;
  const gap = Math.abs(own - other);
  if (gap >= MARKER_LABEL_GAP) return 0;
  return (own <= other ? 1 : -1) * (MARKER_LABEL_GAP - gap);
}

function nudgeStyle(own: number, other: number | null) {
  const shift = labelNudge(own, other);
  return shift === 0
    ? undefined
    : { transform: `translateY(${(shift * FIGURE_HEIGHT_PX) / 100}px)` };
}

/** 옮긴 이름표와 제 선을 잇는 세로줄. 선이 다른 선에 가려도 어느 선의 이름인지 보인다. */
function NudgeLeader({
  own,
  other,
  side,
  className,
}: {
  own: number;
  other: number | null;
  side: "left" | "right";
  className: string;
}) {
  const shift = labelNudge(own, other);
  if (shift === 0) return null;
  const length = (Math.abs(shift) * FIGURE_HEIGHT_PX) / 100;
  return (
    <div
      aria-hidden="true"
      className={`absolute w-px ${side === "left" ? "-left-1" : "-right-1"} ${className}`}
      style={
        shift > 0
          ? { top: "50%", height: length }
          : { bottom: "50%", height: length }
      }
    />
  );
}

/**
 * 백분위(0~100)를 인체 실루엣 높이 비례(bottom %: 10% ~ 93%)로 변환
 */
export function percentileToBodyY(pct: number): number {
  const clamped = Math.max(0, Math.min(100, pct));
  if (clamped <= 25) {
    return 10 + (clamped / 25) * 18; // 10% (발목) ~ 28% (무릎)
  }
  if (clamped <= 50) {
    return 28 + ((clamped - 25) / 25) * 18; // 28% (무릎) ~ 46% (허리)
  }
  if (clamped <= 75) {
    return 46 + ((clamped - 50) / 25) * 26; // 46% (허리) ~ 72% (어깨)
  }
  return 72 + ((clamped - 75) / 25) * 21; // 72% (어깨) ~ 93% (정수리)
}

function CareerLadderView({
  ladder,
  selectedYear,
  onSelectYear,
  isLoading,
  className,
}: {
  ladder: SalaryDistributionResponse["careerLadder"];
  selectedYear?: string;
  onSelectYear?: (year: string) => void;
  isLoading?: boolean;
  /** 카드 안에서는 카드 gap 이 간격을 준다. 블록 흐름에 놓일 때만 여백을 넘긴다. */
  className?: string;
}) {
  if (!ladder?.steps?.length) return null;
  const currentYear = selectedYear || ladder.basePeriod;
  // 임금통계에 없는 직무(예: 기타)는 모든 구간이 비어 있다 — 빈 카드 대신 없다고 말한다
  const hasAnyMedian = ladder.steps.some((step) => step.median != null);
  return (
    <section
      className={cn(
        "rounded-xl border border-carix-border bg-carix-bg/40 p-4",
        className,
      )}
      aria-label="경력 사다리"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h3 className={HEADING.sub}>경력 사다리</h3>
          <p className="text-xs text-carix-text/60">
            같은 직군의 경력 구간별 공식 임금통계
            {currentYear ? ` (${currentYear}년 기준)` : ""} · 지역 전체
          </p>
        </div>
        {ladder.availableYears && ladder.availableYears.length > 1 && (
          <div
            className="flex items-center gap-1 flex-wrap"
            role="group"
            aria-label="기준 연도 선택"
          >
            {ladder.availableYears.map((yr) => {
              const isSelected = currentYear === yr;
              return (
                <Button
                  key={yr}
                  type="button"
                  variant={isSelected ? "default" : "outline"}
                  size="xs"
                  onClick={() => onSelectYear?.(yr)}
                  disabled={isLoading}
                  className={`h-6 px-2 text-xs ${
                    isSelected ? "font-semibold shadow-xs" : "font-normal"
                  }`}
                  aria-pressed={isSelected}
                >
                  {yr}년
                </Button>
              );
            })}
          </div>
        )}
      </div>
      {!hasAnyMedian ? (
        <p className="mt-3 text-sm text-carix-text/70">
          이 직무의 공식 임금통계 없음
        </p>
      ) : (
        <ol className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {ladder.steps.map((step) => {
            const estimated = step.source !== "KOSIS";
            const { label } = bracketLabel(step.bracket);
            return (
              <li
                key={step.bracket}
                className={`rounded-lg border p-3 ${step.bracket === ladder.userBracket ? "border-carix-primary bg-carix-primary/5" : "border-carix-border bg-carix-surface"}`}
              >
                <div className="flex items-center justify-between gap-1">
                  <span className="text-xs font-semibold text-carix-text">
                    {label}
                  </span>
                  {step.bracket === ladder.userBracket && (
                    <span className="text-[10px] font-semibold text-carix-primary">
                      내 구간
                    </span>
                  )}
                </div>
                <div className="mt-2 text-sm font-bold text-carix-text">
                  {formatKoreanSalary(step.median)}
                </div>
                <div className="mt-1 text-[11px] text-carix-text/60">
                  {step.raiseFromPrevious == null
                    ? "직전 구간 대비 —"
                    : `직전 구간 대비 +${step.raiseFromPrevious}%`}
                </div>
                <div
                  className={`mt-2 text-[10px] font-medium ${estimated ? "text-carix-warning" : "text-carix-positive"}`}
                >
                  {estimated
                    ? `추정 · ${wageSourceLabel(step.source)}`
                    : "KOSIS 실측 보간"}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}

export function SalaryDistributionChart({
  data,
  onYearChange,
}: SalaryDistributionChartProps) {
  const [prevPropLadder, setPrevPropLadder] = useState(data.careerLadder);
  const [customLadder, setCustomLadder] = useState<
    SalaryDistributionResponse["careerLadder"] | null
  >(null);
  const [selectedYear, setSelectedYear] = useState<string | undefined>(
    undefined,
  );
  const [isLoadingYear, setIsLoadingYear] = useState(false);

  if (data.careerLadder !== prevPropLadder) {
    setPrevPropLadder(data.careerLadder);
    setCustomLadder(null);
    setSelectedYear(undefined);
  }

  const activeLadder = customLadder ?? data.careerLadder;
  const currentYear = selectedYear ?? data.careerLadder?.basePeriod;

  const handleSelectYear = async (year: string) => {
    if (year === currentYear) return;
    setSelectedYear(year);
    onYearChange?.(year);
    setIsLoadingYear(true);
    try {
      const res = await fetchSalaryDistribution(undefined, year);
      if (res?.careerLadder) {
        setCustomLadder(res.careerLadder);
      }
    } catch {
      // 조회 실패 시 기존 데이터 유지
    } finally {
      setIsLoadingYear(false);
    }
  };

  // 1. 표본 부족 열화 상태 (US-D2, US-D3)
  if (data.status === "INSUFFICIENT_SAMPLE" || data.sampleSize < 10) {
    const minRequired = data.minRequired ?? 10;
    // 공고는 충분한데 공개 연봉이 모자란 경우 — 서버가 공개 연봉 수·결측률만 담은 postingReference 를 싣는다.
    const salaryShort =
      data.postingReference != null && data.sampleSize >= minRequired;
    const disclosedCount = data.postingReference?.sampleSize ?? 0;
    // 건수는 채웠는데 한두 회사의 재등록이라 모자란 경우 — 건수만 보이면 기준을 넘은 것처럼 읽힌다.
    const companyCount = data.postingReference?.companyCount;
    const companiesShort =
      salaryShort &&
      disclosedCount >= minRequired &&
      companyCount != null &&
      companyCount < minRequired;
    return (
      <EmptyState
        data-testid="insufficient-sample-state"
        tone="warning"
        icon="⚠️ 표본 부족"
        title={
          salaryShort
            ? "공개 연봉이 부족하여 연봉 분포를 산출할 수 없습니다."
            : "동료집단 표본 수가 부족하여 연봉 분포를 산출할 수 없습니다."
        }
        description={
          companiesShort ? (
            <>
              동료집단 공고 {data.sampleSize}건 중{" "}
              <strong className="text-carix-warning">
                공개 연봉 {disclosedCount}건, 회사 {companyCount}곳
              </strong>{" "}
              (같은 회사 공고가 대부분이라 서로 다른 회사 {minRequired}곳 이상
              필요)
            </>
          ) : salaryShort ? (
            <>
              동료집단 공고 {data.sampleSize}건 중 공개 연봉{" "}
              <strong className="text-carix-warning">{disclosedCount}건</strong>{" "}
              (최소 기준: {minRequired}건 이상 필요)
            </>
          ) : (
            <>
              현재 동료집단 공고 수:{" "}
              <strong className="text-carix-warning">
                {data.sampleSize}건
              </strong>{" "}
              (최소 기준: {minRequired}건 이상 필요)
            </>
          )
        }
      >
        {data.peerGroup?.appliedConditions && (
          <p className="text-xs text-carix-text/60 bg-carix-surface rounded-lg p-2 border border-carix-border inline-block shadow-xs">
            적용된 비교 조건:{" "}
            {formatAppliedConditions(data.peerGroup.appliedConditions)}
          </p>
        )}
        <CareerLadderView
          className="mt-6"
          ladder={activeLadder}
          selectedYear={currentYear}
          onSelectYear={handleSelectYear}
          isLoading={isLoadingYear}
        />
      </EmptyState>
    );
  }

  const { postingReference, baseline, userPosition, peerGroup } = data;

  const hasPostingSalaries = Boolean(
    postingReference &&
    (postingReference.sampleSize ?? 0) > 0 &&
    postingReference.min != null &&
    postingReference.max != null,
  );

  // 5수치 데이터
  const min = postingReference?.min ?? 0;
  const p25 = postingReference?.p25 ?? 0;
  const median = postingReference?.median ?? 0;
  const p75 = postingReference?.p75 ?? 0;
  const max = postingReference?.max ?? 0;
  const officialMedian = baseline?.median ?? null;

  // 백분위 상위 % 계산 (100 - percentile)
  const topPercentText = userPosition
    ? `상위 ${Math.max(1, 100 - userPosition.percentile)}% (백분위 ${userPosition.percentile}%)`
    : "미등록";

  // 금액 기준 백분위 추정 (공식 임금통계 기준선 등의 위치 계산용)
  const estimatePercentile = (won: number | null | undefined): number => {
    if (won == null || !hasPostingSalaries) return 50;
    if (won <= min) return 5;
    if (won >= max) return 95;
    if (won <= p25) {
      return 5 + ((won - min) / (p25 - min || 1)) * 20;
    }
    if (won <= median) {
      return 25 + ((won - p25) / (median - p25 || 1)) * 25;
    }
    if (won <= p75) {
      return 50 + ((won - median) / (p75 - median || 1)) * 25;
    }
    return 75 + ((won - p75) / (max - p75 || 1)) * 20;
  };

  const userBodyPart = userPosition
    ? getBodyPartInfo(userPosition.percentile)
    : null;
  const userBand = userPosition ? salaryBand(userPosition.percentile) : null;

  // 신체 그래픽 상의 각 지표 위치 (bottom %: 10% ~ 93%)
  const userVisualY = userPosition
    ? percentileToBodyY(userPosition.percentile)
    : null;
  const medianVisualY = percentileToBodyY(50); // 공고 중앙값은 표본의 50%
  const officialVisualY = officialMedian
    ? percentileToBodyY(estimatePercentile(officialMedian))
    : null;

  return (
    <Card data-testid="salary-distribution-chart">
      {/* 1. 비교 조건 및 사용자 연봉 위치 요약 — 제목은 화면의 섹션 제목이 갖는다 */}
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-carix-border pb-5">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-medium text-carix-text">
            {peerGroup?.appliedConditions
              ? formatAppliedConditions(peerGroup.appliedConditions)
              : "동료집단 기준선"}
          </p>
          {peerGroup?.fallbackLevel && peerGroup.fallbackLevel > 1 && (
            <ToneBadge tone="warning">
              완화 {peerGroup.fallbackLevel}단계
            </ToneBadge>
          )}
        </div>

        {userPosition ? (
          <div className="text-right">
            <span className="text-xs text-carix-text/60">내 연봉 위치</span>
            <div className="font-display text-lg font-bold text-carix-positive">
              {topPercentText}
            </div>
          </div>
        ) : (
          <div className="text-right">
            <span className="text-xs text-carix-text/60">내 연봉 위치</span>
            <div className="text-sm font-semibold text-carix-text/50">
              온보딩 연봉 미등록
            </div>
          </div>
        )}
      </div>

      {!hasPostingSalaries ? (
        <EmptyState
          data-testid="no-disclosed-salary-state"
          variant="plain"
          tone="info"
          icon="ℹ️ 공개 연봉 미제공"
          title="동료집단 공고 중 공개된 연봉 정보가 없습니다."
          description={
            <>
              총 동료집단 공고{" "}
              <strong className="text-carix-text">{data.sampleSize}건</strong>{" "}
              중 급여가 공개된 공고 표본이 없어 공고 기반 5분위 연봉 통계를
              산출할 수 없습니다.
              {officialMedian && (
                <span className="mt-2 block">
                  대신 하단의{" "}
                  <strong>
                    국가 공식 임금통계 기준선(
                    {formatKoreanSalary(officialMedian)})
                  </strong>
                  을 참고하실 수 있습니다.
                </span>
              )}
            </>
          }
        />
      ) : (
        <>
          {/* 2. "머리-어깨-무릎-발" 신체 형태 인터랙티브 시각화 */}
          <div className="rounded-2xl bg-carix-bg/40 p-5 sm:p-6 border border-carix-border/70">
            {/* 상단 브리핑 헤드라인 */}
            {userPosition && userBodyPart && (
              <div className="mb-6 flex items-center gap-3 rounded-xl bg-carix-surface p-4 border border-carix-border shadow-2xs">
                <span className="text-3xl">{userBodyPart.icon}</span>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-sm text-carix-text sm:text-base">
                      회원님의 연봉은 현재{" "}
                      <span className="text-carix-positive underline decoration-carix-positive/40 underline-offset-4">
                        [{userBodyPart.name}]
                      </span>
                      에 위치해 있어요!
                    </span>
                    <ToneBadge tone="positive">{userBodyPart.label}</ToneBadge>
                  </div>
                  <p className="mt-0.5 text-xs text-carix-text/70">
                    {userBodyPart.desc}
                  </p>
                </div>
              </div>
            )}

            {/* 인체 실루엣 및 신체 부위별 연봉 맵핑 영역 */}
            <div className="grid grid-cols-1 md:grid-cols-12 items-center gap-6">
              {/* 왼쪽: 세련된 인체 실루엣 그래픽 (4 컬럼) */}
              <div className="md:col-span-4 flex flex-col items-center justify-center py-2">
                <div
                  className="relative w-44 flex items-center justify-center"
                  style={{ height: FIGURE_HEIGHT_PX }}
                >
                  {/* 신체 실루엣 SVG */}
                  <svg
                    viewBox="0 0 100 240"
                    className="h-full w-28 overflow-visible drop-shadow-xs"
                    aria-label="연봉 위치 인체 실루엣"
                  >
                    <defs>
                      <linearGradient
                        id="userBodyGlow"
                        x1="0%"
                        y1="100%"
                        x2="0%"
                        y2="0%"
                      >
                        <stop
                          offset={`${userVisualY}%`}
                          stopColor="var(--carix-positive)"
                          stopOpacity="0.35"
                        />
                        <stop
                          offset={`${userVisualY}%`}
                          stopColor="currentColor"
                          stopOpacity="0.08"
                        />
                      </linearGradient>
                    </defs>

                    {/* 머리 */}
                    <circle
                      cx="50"
                      cy="22"
                      r="13"
                      className="fill-current text-carix-text/25 transition-all duration-500"
                    />

                    {/* 몸통·두 다리. 팔은 따로 그려 몸통과 틈을 둔다 — 한 윤곽이면 팔이 몸통에 묻혀 안 보였다. */}
                    <path
                      d="M38 40 C42 38.5 46 38 50 38 C54 38 58 38.5 62 40 L67 44 C68.5 45 69 47 68.5 49 L64.5 98 L64 118 L64 155 C64 162 65 188 66 205 L68 215 C68 218 66 220 62 220 L55 220 C53 220 52 218 52 215 L53 205 C53 190 52 165 52 155 L51 126 C50.5 124 49.5 124 49 126 L48 155 C48 165 47 190 47 205 L48 215 C48 218 47 220 45 220 L38 220 C34 220 32 218 32 215 L34 205 C35 188 36 162 36 155 L36 118 L35.5 98 L31.5 49 C31 47 31.5 45 33 44 Z"
                      className="fill-current text-carix-text/25 transition-all duration-500"
                    />
                    <path
                      d="M28 46 C25 47 23 50 23 54 L21 98 C21 104 23 108 26 108 C29 108 31 104 31 99 L30.5 58 C30.5 52 30 48 28 46 Z"
                      className="fill-current text-carix-text/25 transition-all duration-500"
                    />
                    <path
                      d="M72 46 C75 47 77 50 77 54 L79 98 C79 104 77 108 74 108 C71 108 69 104 69 99 L69.5 58 C69.5 52 70 48 72 46 Z"
                      className="fill-current text-carix-text/25 transition-all duration-500"
                    />

                    {/* 주요 신체 부위 가이드 마커 점선 (SVG 내부 가이드) */}
                    {/* 정수리 (93%) */}
                    <line
                      x1="10"
                      y1="16"
                      x2="90"
                      y2="16"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                    {/* 어깨 (72%) */}
                    <line
                      x1="10"
                      y1="55"
                      x2="90"
                      y2="55"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                    {/* 허리 (46%) */}
                    <line
                      x1="10"
                      y1="115"
                      x2="90"
                      y2="115"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                    {/* 무릎 (28%) */}
                    <line
                      x1="10"
                      y1="164"
                      x2="90"
                      y2="164"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                  </svg>

                  {/* 1. 공고 중앙값 수평 마커 (허리 50% 기준선) */}
                  <div
                    className="absolute -left-2 -right-2 z-10 flex items-center transition-all duration-500"
                    style={{ bottom: `${medianVisualY}%` }}
                    title={`공고 중앙값: ${formatKoreanSalary(median)}`}
                  >
                    <div className="h-[1.5px] w-full bg-carix-info/60 border-b border-dashed border-carix-info/70" />
                    <div className="absolute left-1/2 -translate-x-1/2 h-2.5 w-2.5 rounded-full bg-carix-info ring-2 ring-white dark:ring-carix-surface shadow-xs" />
                    <NudgeLeader
                      own={medianVisualY}
                      other={userVisualY}
                      side="left"
                      className="bg-carix-info/80"
                    />
                    <span
                      className="absolute -left-1 -translate-x-full rounded bg-carix-info/90 px-1.5 py-0.5 text-[9px] font-bold text-white shadow-xs whitespace-nowrap"
                      style={nudgeStyle(medianVisualY, userVisualY)}
                    >
                      공고중앙
                    </span>
                  </div>

                  {/* 2. 공식 임금통계 기준선 수평 마커 (KOSIS 기준선) */}
                  {officialVisualY != null && (
                    <div
                      className="absolute -left-2 -right-2 z-10 flex items-center transition-all duration-500"
                      style={{ bottom: `${officialVisualY}%` }}
                      title={`공식 임금통계 기준선: ${formatKoreanSalary(officialMedian)}`}
                    >
                      <div className="h-[1.5px] w-full bg-carix-warning/70 border-b border-dashed border-carix-warning/80" />
                      <div className="absolute left-1/2 -translate-x-1/2 h-2.5 w-2.5 rounded-full bg-carix-warning ring-2 ring-white dark:ring-carix-surface shadow-xs" />
                      <NudgeLeader
                        own={officialVisualY}
                        other={userVisualY}
                        side="right"
                        className="bg-carix-warning/80"
                      />
                      <span
                        className="absolute -right-1 translate-x-full rounded bg-carix-warning/90 px-1.5 py-0.5 text-[9px] font-bold text-white shadow-xs whitespace-nowrap"
                        style={nudgeStyle(officialVisualY, userVisualY)}
                      >
                        공식기준
                      </span>
                    </div>
                  )}

                  {/* 3. 사용자 연봉 실루엣 스캔 빔 & 핀 포인트 */}
                  {userPosition && userVisualY != null && (
                    <div
                      className="absolute -left-3 -right-3 z-20 flex items-center transition-all duration-500"
                      style={{ bottom: `${userVisualY}%` }}
                      title={`내 연봉: ${formatKoreanSalary(userPosition.grossSalary)}`}
                    >
                      {/* 수평 스캔 레이저 라인 */}
                      <div className="h-0.5 w-full bg-carix-positive shadow-[0_0_8px_var(--carix-positive)]" />
                      {/* 중심 핀 포인트 헤드 */}
                      <div className="absolute left-1/2 -translate-x-1/2 h-3.5 w-3.5 rounded-full bg-carix-positive ring-4 ring-white dark:ring-carix-surface shadow-md" />
                      <span className="absolute -left-1 -translate-x-full rounded bg-carix-positive px-1.5 py-0.5 text-[10px] font-bold text-white shadow whitespace-nowrap">
                        내 연봉
                      </span>
                    </div>
                  )}
                </div>

                <span className="mt-2 text-[11px] text-carix-text/50 font-medium">
                  인체 높이별 연봉 백분위
                </span>
              </div>

              {/* 오른쪽: 신체 부위별 연봉 레벨 및 시장 기준선 (8 컬럼) */}
              <div className="md:col-span-8 flex flex-col justify-between space-y-2.5">
                {/* 1. 머리 (상위 25%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userBand === "head"
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">👑</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        머리
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (상위 25%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(p75)} 이상
                    </span>
                    {userPosition && userBand === "head" && (
                      <ToneBadge tone="positive" className="ml-2">
                        내 위치 ({formatKoreanSalary(userPosition.grossSalary)})
                      </ToneBadge>
                    )}
                  </div>
                </div>

                {/* 2. 어깨 (상위 25~50%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userBand === "shoulder"
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">👔</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        어깨
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (상위 25~50%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(median)} ~ {formatKoreanSalary(p75)}
                    </span>
                    {userPosition && userBand === "shoulder" && (
                      <ToneBadge tone="positive" className="ml-2">
                        내 위치 ({formatKoreanSalary(userPosition.grossSalary)})
                      </ToneBadge>
                    )}
                  </div>
                </div>

                {/* 3. 허리 (동료집단 중심 기준선: 공고 중앙값 & 공식 임금통계) */}
                <div className="flex flex-col gap-1.5 rounded-xl p-2.5 border transition-all bg-carix-surface/80 border-carix-border/80">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-base">👖</span>
                      <div>
                        <span className="font-bold text-xs text-carix-text">
                          허리
                        </span>
                        <span className="ml-1.5 text-[11px] text-carix-text/50">
                          (중앙값 · 위아래를 가르는 선)
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* 허리 부근의 핵심 비교 기준선 2종 */}
                  <div className="mt-1 grid grid-cols-1 sm:grid-cols-2 gap-1.5 pt-1 border-t border-carix-border/50 text-xs">
                    <div className="flex items-center justify-between rounded bg-carix-info/5 px-2 py-1 border border-carix-info/20">
                      <span className="flex items-center gap-1 text-[11px] font-medium text-carix-info">
                        <span className="inline-block h-2 w-2 rounded-full bg-carix-info" />
                        공고 중앙값
                      </span>
                      <span className="font-bold text-carix-info">
                        {formatKoreanSalary(median)}
                      </span>
                    </div>

                    {officialMedian != null && (
                      <div className="flex items-center justify-between rounded bg-carix-warning/5 px-2 py-1 border border-carix-warning/20">
                        <span className="flex items-center gap-1 text-[11px] font-medium text-carix-warning">
                          <span className="inline-block h-2 w-2 rounded-full bg-carix-warning" />
                          공식 기준선 (
                          {wageSourceLabel(baseline?.source ?? "UNKNOWN")})
                        </span>
                        <span className="font-bold text-carix-warning">
                          {formatKoreanSalary(officialMedian)}
                        </span>
                      </div>
                    )}
                  </div>
                </div>

                {/* 4. 무릎 (하위 25~50%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userBand === "knee"
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">🦵</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        무릎
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (하위 25~50%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(p25)} ~ {formatKoreanSalary(median)}
                    </span>
                    {userPosition && userBand === "knee" && (
                      <ToneBadge tone="positive" className="ml-2">
                        내 위치 ({formatKoreanSalary(userPosition.grossSalary)})
                      </ToneBadge>
                    )}
                  </div>
                </div>

                {/* 5. 발목 (하위 25%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userBand === "ankle"
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">👟</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        발목
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (하위 25%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(min)} ~ {formatKoreanSalary(p25)}
                    </span>
                    {userPosition && userBand === "ankle" && (
                      <ToneBadge tone="positive" className="ml-2">
                        내 위치 ({formatKoreanSalary(userPosition.grossSalary)})
                      </ToneBadge>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* 3. 5수치 상세 카드 그리드 (공고 표본 분위별 기준 연봉) */}
          <div className="mt-4">
            <div className="mb-2.5 flex items-center justify-between text-xs text-carix-text/70">
              <span className="font-semibold text-carix-text/90">
                📊 공고 표본 5분위 기준 연봉 (참고치)
              </span>
              <span className="text-[11px] text-carix-text/50">
                공개 표본 {postingReference?.sampleSize ?? 0}건 기준
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 sm:grid-cols-5 text-center text-xs">
              <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
                <span className="text-carix-text/60">최소</span>
                <div className="mt-1 font-semibold text-carix-text">
                  {formatKoreanSalary(min)}
                </div>
              </div>
              <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
                <span className="text-carix-text/60">하위 25%</span>
                <div className="mt-1 font-semibold text-carix-text">
                  {formatKoreanSalary(p25)}
                </div>
              </div>
              <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
                <span className="text-carix-text/60">공고 중앙값 (50%)</span>
                <div className="mt-1 font-semibold text-carix-text">
                  {formatKoreanSalary(median)}
                </div>
              </div>
              <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
                <span className="text-carix-text/60">상위 25% (75%)</span>
                <div className="mt-1 font-semibold text-carix-text">
                  {formatKoreanSalary(p75)}
                </div>
              </div>
              <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
                <span className="text-carix-text/60">최대</span>
                <div className="mt-1 font-semibold text-carix-text">
                  {formatKoreanSalary(max)}
                </div>
              </div>
            </div>
          </div>

          {/* 제시 연봉 추이 (기능 명세 §4.6, US-D3) */}
          {((data.trend?.length ?? 0) > 0 || data.trendComparison) && (
            <SalaryTrend
              trend={data.trend ?? []}
              comparison={data.trendComparison}
            />
          )}
        </>
      )}

      {/* 4. 하단 각주 및 데이터 신뢰도 메타데이터 (US-D3) */}
      <div className="mt-2 rounded-lg bg-carix-bg/40 p-3.5 text-xs text-carix-text/70 border border-carix-border/60 space-y-1.5">
        <p className="flex items-center gap-1.5">
          <span className="font-semibold text-carix-text">📌 판정 기준선:</span>
          {baseline ? (
            <span>
              {wageBasisLabel(baseline.basis ?? "AGE_BAND_PROXY")} ·{" "}
              {wageSourceLabel(baseline.source ?? "UNKNOWN")} (
              {wageDefinitionLabel(baseline.wageDefinition ?? "공식 임금통계")})
              {/* 금액은 허리 카드가 보인다 — 공고 연봉이 없어 카드가 없을 때만 여기서 보인다. */}
              {!hasPostingSalaries &&
                ` — 중앙값 ${formatKoreanSalary(baseline.median)}`}
            </span>
          ) : (
            // 임금통계에 없는 직무(예: 기타)는 기준선이 없다 — 있는 것처럼 기본값을 채우지 않는다
            <span>공식 임금통계 기준선 없음</span>
          )}
        </p>
        <p className="flex items-center gap-1.5">
          <span className="font-semibold text-carix-text">
            📊 공고 표본 통계:
          </span>
          <span>
            총 동료집단 {data.sampleSize}건 중 공개연봉{" "}
            {postingReference?.sampleSize ?? 0}건 유효 (공개연봉 미공개 결측률:{" "}
            <strong>{postingReference?.missingRate?.toFixed(1) ?? 0}%</strong>)
          </span>
        </p>
        <p className="text-[11px] text-carix-text/50">
          * 채용공고의 연봉은 미공개 결측률이 높아 참고 범위로만 병기되며, 핵심
          결정 신호 판정은 국가 공식 임금통계 중앙값을 기준으로 이루어집니다.
        </p>
      </div>
      <CareerLadderView
        ladder={activeLadder}
        selectedYear={currentYear}
        onSelectYear={handleSelectYear}
        isLoading={isLoadingYear}
      />
    </Card>
  );
}
