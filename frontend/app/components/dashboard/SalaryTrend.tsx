import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import type {
  SalaryTrendPoint,
  TrendComparison,
  TrendPeriod,
  TrendVerdict,
} from "../../lib/dashboardMarketApi";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import { sourceLabel } from "../../lib/sourceLabel";

const TABLE_ID = "salary-trend-table";
const MINUS = "\u2212";

const VERDICT_TEXT: Record<TrendVerdict, string> = {
  UP: "상승",
  DOWN: "하락",
  FLAT: "뚜렷한 변화 없음",
  INSUFFICIENT_SAMPLE: "판단 보류 (표본 부족)",
};

/** `2026-04 ~ 06`. 연도가 다르면 `2026-12 ~ 2027-02`. */
function periodLabel({ from, to }: TrendPeriod): string {
  const sameYear = from.slice(0, 4) === to.slice(0, 4);
  return `${from} ~ ${sameYear ? to.slice(5) : to}`;
}

/** 상승·하락일 때만 `+1,000만 원 (+25%)` 꼴의 차이. 비교할 수 없으면 null. */
function deltaLabel({ recent, previous, verdict }: TrendComparison) {
  if (verdict !== "UP" && verdict !== "DOWN") return null;
  if (recent.median == null || previous.median == null || previous.median <= 0)
    return null;
  const diff = recent.median - previous.median;
  const sign = diff < 0 ? MINUS : "+";
  const percent = Math.abs(Math.round((diff / previous.median) * 100));
  return `${sign}${formatKoreanSalary(Math.abs(diff))} (${sign}${percent}%)`;
}

function PeriodCell({ period }: { period: TrendPeriod }) {
  return (
    <div className="flex flex-1 flex-col gap-0.5 rounded-lg border border-carix-border bg-carix-bg/60 p-2.5">
      <span className="text-[11px] text-carix-text/60">
        {periodLabel(period)}
      </span>
      {period.median != null ? (
        <span className="text-sm font-semibold text-carix-info">
          {formatKoreanSalary(period.median)}
        </span>
      ) : (
        <span className="text-sm text-carix-text/40 italic">표본 부족</span>
      )}
      <span className="text-[11px] text-carix-text/50">
        {`공개 ${period.disclosedSampleSize}건`}
      </span>
    </div>
  );
}

function summaryText(comparison: TrendComparison): string {
  const median = (period: TrendPeriod) =>
    period.median != null ? formatKoreanSalary(period.median) : "표본 부족";
  return `동료집단 중앙 제시 연봉 ${VERDICT_TEXT[comparison.verdict]}. 이전 ${periodLabel(comparison.previous)} ${median(comparison.previous)}, 최근 ${periodLabel(comparison.recent)} ${median(comparison.recent)}.`;
}

/**
 * 동료집단 중앙 제시 연봉의 이전·최근 3개월 비교(기능 명세 US-D3). 「시장이 오르는가」만 답하고,
 * 월별 표본 수·결측률 표는 토글 뒤로 접는다.
 */
export function SalaryTrend({
  trend,
  comparison,
}: {
  trend: readonly SalaryTrendPoint[];
  comparison: TrendComparison | null | undefined;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const delta = comparison ? deltaLabel(comparison) : null;

  return (
    <div
      className="mt-4 flex flex-col gap-1.5"
      data-testid="salary-trend-section"
    >
      <div className="flex items-center justify-between gap-2 text-xs">
        <span className="font-semibold text-carix-text/90">
          📈 동료집단 중앙 제시 연봉 추이
        </span>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          aria-expanded={isOpen}
          aria-controls={TABLE_ID}
          onClick={() => setIsOpen((open) => !open)}
        >
          {isOpen ? "월별 접기" : "월별 보기"}
        </Button>
      </div>
      {comparison && (
        <>
          <div className="flex items-stretch gap-2" aria-hidden="true">
            <PeriodCell period={comparison.previous} />
            <span className="self-center text-carix-text/40">→</span>
            <PeriodCell period={comparison.recent} />
          </div>
          <div
            className="flex flex-wrap items-baseline gap-2 text-xs"
            aria-hidden="true"
          >
            <span className="font-semibold text-carix-text">
              {VERDICT_TEXT[comparison.verdict]}
            </span>
            {delta && <span className="text-carix-info">{delta}</span>}
          </div>
          <p className="sr-only">{summaryText(comparison)}</p>
          {comparison.sources.length > 0 && (
            <p className="text-[11px] text-carix-text/50">
              {`${comparison.sources.map(sourceLabel).join("·")} 무작위 표본 기준 — 매달 같은 방식으로 뽑은 공고끼리 비교합니다.`}
            </p>
          )}
          {comparison.inProgress.length > 0 && (
            <p className="text-[11px] text-carix-text/50">
              {`${comparison.inProgress.join("·")} 은 집계 중이라 비교에서 제외`}
            </p>
          )}
        </>
      )}
      <p className="text-[11px] text-carix-text/50">
        연봉 공개 비율이 달마다 달라, 변동이 시장이 아니라 공개 성향 탓일 수
        있습니다.
      </p>
      <div id={TABLE_ID} hidden={!isOpen}>
        <p className="mb-1 text-[11px] text-carix-text/50">
          월별 표는 전체 수집 공고 기준이라 위 비교(무작위 표본)와 값이 다를 수
          있습니다.
        </p>
        <Table variant="plain">
          <TableHeader>
            <TableRow>
              <TableHead>등록월</TableHead>
              <TableHead>중앙 제시 연봉</TableHead>
              <TableHead className="text-right">전체 공고</TableHead>
              <TableHead className="text-right">공개 표본</TableHead>
              <TableHead className="text-right">결측률</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {trend.map((point) => (
              <TableRow key={point.yearMonth}>
                <TableCell className="font-medium">{point.yearMonth}</TableCell>
                <TableCell className="font-semibold">
                  {point.median != null ? (
                    <span className="text-carix-info">
                      {formatKoreanSalary(point.median)}
                    </span>
                  ) : (
                    <span className="text-carix-text/40 italic">
                      표본 부족 (미산출)
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-right text-carix-text/80">
                  {point.sampleSize}건
                </TableCell>
                <TableCell className="text-right text-carix-text/80">
                  {point.disclosedSampleSize}건
                </TableCell>
                <TableCell className="text-right text-carix-text/60">
                  {point.missingRate.toFixed(1)}%
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
