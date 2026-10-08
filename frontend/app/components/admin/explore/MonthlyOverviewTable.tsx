import { Link } from "react-router";
import type { MonthlyOverview } from "../../../lib/collectorApi";
import { isThinFamily, isThinSample } from "../../../lib/dataExplore";
import { familyLabel, sourceLabel } from "../../../lib/sourceLabel";

const FAMILIES = ["DEVELOPER", "DATA", "AI"] as const;

const GROUP_TH = "px-2 py-1 text-center border-b border-carix-border";
const SUB_TH = "px-2 py-1 text-right";

function Cell({ value, thin }: { value: number; thin: boolean }) {
  return (
    <td
      data-thin={String(thin)}
      className={`px-2 py-1 text-right ${thin ? "text-carix-warning font-semibold" : ""}`}
    >
      {value}
    </td>
  );
}

function Plain({ value }: { value: number }) {
  return <td className="px-2 py-1 text-right">{value}</td>;
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
      <p className="text-xs text-carix-text/60">
        주황 = 상한 적용 표본 200 미만 또는 직무 표본 10 미만 · 상한 적용 = 같은
        회사 월 3건까지 · 정기 = 그 달 처음 관측된 공고
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead className="text-carix-text/60">
            <tr>
              <th rowSpan={2} className="px-2 py-1 text-left align-bottom">
                월
              </th>
              <th colSpan={3} className={GROUP_TH}>
                {sourceLabel("JOBKOREA")}
              </th>
              <th colSpan={1} className={GROUP_TH}>
                {sourceLabel("SARAMIN")}
              </th>
              <th colSpan={3} className={GROUP_TH}>
                {sourceLabel("WANTED")}
              </th>
              <th colSpan={FAMILIES.length} className={GROUP_TH}>
                직무별 표본 ({sourceLabel(sampleSource ?? "JOBKOREA")})
              </th>
            </tr>
            <tr>
              <th className={SUB_TH}>표본</th>
              <th className={SUB_TH}>상한 적용</th>
              <th className={SUB_TH}>정기</th>
              <th className={SUB_TH}>정기</th>
              <th className={SUB_TH}>표본</th>
              <th className={SUB_TH}>상한 적용</th>
              <th className={SUB_TH}>정기</th>
              {FAMILIES.map((f) => (
                <th key={f} className={SUB_TH}>
                  {familyLabel(f)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.months.map((m) => (
              <tr key={m.month} className="border-t border-carix-border">
                <td className="px-2 py-1">
                  <Link
                    to={`/admin/data?tab=postings&month=${m.month}`}
                    title="이 달 공고 보기"
                    className="text-carix-info underline"
                  >
                    {m.month}
                  </Link>
                  {m.current && (
                    <span className="ml-2 text-carix-text/60">수집 중</span>
                  )}
                </td>
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
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
