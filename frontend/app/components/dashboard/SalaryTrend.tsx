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

import type { SalaryTrendPoint } from "../../lib/dashboardMarketApi";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import { toSalaryTrendSeries } from "../../lib/salaryTrend";
import { Sparkline } from "./Sparkline";

const TABLE_ID = "salary-trend-table";
/** 점 하나는 추세가 아니다. */
const MIN_POINTS = 2;

/**
 * 동료집단 등록월별 중앙 제시 연봉(기능 명세 US-D3). 「시장이 오르는가」만 답하면 돼서
 * 스파크라인과 시작·끝 값으로 줄이고, 월별 표본 수·결측률 표는 토글 뒤로 접는다.
 */
export function SalaryTrend({ trend }: { trend: readonly SalaryTrendPoint[] }) {
  const [isOpen, setIsOpen] = useState(false);
  const series = toSalaryTrendSeries(trend);
  const points = series.segments.flat();
  const hasLine = points.length >= MIN_POINTS;
  const first = hasLine ? formatKoreanSalary(points[0].value) : null;
  const last = hasLine
    ? formatKoreanSalary(points[points.length - 1].value)
    : null;
  const missing = series.slots - points.length;
  const period = `최근 ${series.slots}개월`;

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
      {hasLine && (
        <>
          <div className="flex items-center gap-3 text-xs">
            <Sparkline series={series} baseline={null} />
            <span className="text-carix-text/60">{period}</span>
            <span aria-hidden="true" className="font-medium text-carix-info">
              {`${first} → ${last}`}
            </span>
          </div>
          <p className="sr-only">
            {`${period} 동료집단 중앙 제시 연봉이 ${first}에서 ${last}까지 변했습니다.`}
            {missing > 0 && ` 값이 없는 달 ${missing}개월.`}
          </p>
        </>
      )}
      {missing > 0 && (
        <p className="text-[11px] text-carix-text/50">
          {`값 없는 달 ${missing}개월 (공개 연봉 표본 부족)`}
        </p>
      )}
      <p className="text-[11px] text-carix-text/50">
        공고 등록월 기준. 연봉 공개 비율이 달마다 달라, 변동이 시장이 아니라
        공개 성향 탓일 수 있습니다.
      </p>
      <div id={TABLE_ID} hidden={!isOpen}>
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
