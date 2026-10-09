import { Link } from "react-router";
import { Badge } from "@/components/ui/badge";
import type { MonthlyOverview } from "../../../lib/collectorApi";
import {
  isThinFamily,
  isThinSample,
  SAMPLE_COMPANY_CAP,
} from "../../../lib/dataExplore";
import { familyLabel, sourceLabel } from "../../../lib/sourceLabel";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { CELL, HEAD, META, NUM } from "./exploreStyles";

const FAMILIES = ["DEVELOPER", "DATA", "AI"] as const;

const GROUP_TH = `${HEAD} text-center border-b border-carix-border`;
const SUB_TH = `${HEAD} ${NUM}`;

function Cell({ value, thin }: { value: number; thin: boolean }) {
  return (
    <TableCell
      data-thin={String(thin)}
      className={`${CELL} ${NUM} ${thin ? "text-carix-warning font-semibold" : ""}`}
    >
      {value}
      {thin && <span className="sr-only">(부족)</span>}
    </TableCell>
  );
}

function Plain({ value }: { value: number }) {
  return <TableCell className={`${CELL} ${NUM}`}>{value}</TableCell>;
}

/** 월 × 원천·직무 표. 상한 적용 200 미만·직무 표본 10 미만을 강조하되 수집 중인 당월은 제외한다. */
export function MonthlyOverviewTable({
  data,
  sampleSource,
}: {
  data: MonthlyOverview;
  sampleSource?: string;
}) {
  if (data.months.length === 0) {
    return (
      <p className="text-xs text-carix-text/60">
        해당 조건의 데이터가 없습니다
      </p>
    );
  }
  return (
    <div className="space-y-2">
      <p className={META}>
        <span>
          <span className="font-semibold text-carix-warning">주황</span> = 상한
          적용 표본 200 미만 또는 직무 표본 10 미만
        </span>
        <span>상한 적용 = 같은 회사 월 {SAMPLE_COMPANY_CAP}건까지</span>
        <span>정기 = 그 달 처음 관측된 공고</span>
      </p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead rowSpan={2} className={`${HEAD} align-bottom`}>
              월
            </TableHead>
            <TableHead colSpan={3} className={GROUP_TH}>
              {sourceLabel("JOBKOREA")}
            </TableHead>
            <TableHead colSpan={1} className={GROUP_TH}>
              {sourceLabel("SARAMIN")}
            </TableHead>
            <TableHead colSpan={3} className={GROUP_TH}>
              {sourceLabel("WANTED")}
            </TableHead>
            <TableHead colSpan={FAMILIES.length} className={GROUP_TH}>
              직무별 표본 ({sourceLabel(sampleSource ?? "JOBKOREA")})
            </TableHead>
          </TableRow>
          <TableRow>
            <TableHead className={SUB_TH}>표본</TableHead>
            <TableHead className={SUB_TH}>상한 적용</TableHead>
            <TableHead className={SUB_TH}>정기</TableHead>
            <TableHead className={SUB_TH}>정기</TableHead>
            <TableHead className={SUB_TH}>표본</TableHead>
            <TableHead className={SUB_TH}>상한 적용</TableHead>
            <TableHead className={SUB_TH}>정기</TableHead>
            {FAMILIES.map((f) => (
              <TableHead key={f} className={SUB_TH}>
                {familyLabel(f)}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.months.map((m) => (
            <TableRow key={m.month}>
              <TableCell className={`${CELL} tabular-nums`}>
                <Link
                  to={`/admin/data?tab=postings&month=${m.month}`}
                  title="이 달 공고 보기"
                  className="text-carix-info underline"
                >
                  {m.month}
                </Link>
                {m.current && (
                  <Badge variant="secondary" className="ml-2">
                    수집 중
                  </Badge>
                )}
              </TableCell>
              <Plain value={m.jobkoreaSampled} />
              <Cell
                value={m.jobkoreaSampledCapped}
                thin={!m.current && isThinSample(m.jobkoreaSampledCapped)}
              />
              <Plain value={m.jobkoreaCrawled} />
              <Plain value={m.saraminCrawled} />
              <Plain value={m.wantedSampled} />
              <Cell
                value={m.wantedSampledCapped}
                thin={!m.current && isThinSample(m.wantedSampledCapped)}
              />
              <Plain value={m.wantedCrawled} />
              {FAMILIES.map((f) => (
                <Cell
                  key={f}
                  value={m.sampledByFamily[f] ?? 0}
                  thin={!m.current && isThinFamily(m.sampledByFamily[f] ?? 0)}
                />
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
