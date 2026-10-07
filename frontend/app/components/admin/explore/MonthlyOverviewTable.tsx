import { Link } from "react-router";
import type { MonthlyOverview } from "../../../lib/collectorApi";
import { isThinFamily, isThinSample } from "../../../lib/dataExplore";

const FAMILIES = ["DEVELOPER", "DATA", "AI", "OTHER"] as const;

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

/** 월 × 원천·직무 표. 표본 200 미만·직무 표본 10 미만을 강조한다. */
export function MonthlyOverviewTable({ data }: { data: MonthlyOverview }) {
  if (data.months.length === 0) {
    return (
      <p className="text-xs text-carix-text/60">
        해당 조건의 데이터가 없습니다
      </p>
    );
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs">
        <thead>
          <tr className="text-carix-text/60">
            <th className="px-2 py-1 text-left">월</th>
            <th className="px-2 py-1 text-right">잡코리아 표본</th>
            <th className="px-2 py-1 text-right">상한 적용</th>
            <th className="px-2 py-1 text-right">잡코리아 정기</th>
            <th className="px-2 py-1 text-right">사람인 정기</th>
            <th className="px-2 py-1 text-right">원티드 표본</th>
            <th className="px-2 py-1 text-right">원티드 상한 적용</th>
            <th className="px-2 py-1 text-right">원티드 정기</th>
            {FAMILIES.map((f) => (
              <th key={f} className="px-2 py-1 text-right">
                {f}
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
                  className="text-carix-info underline"
                >
                  {m.month}
                </Link>
              </td>
              <Cell
                value={m.jobkoreaSampled}
                thin={isThinSample(m.jobkoreaSampled)}
              />
              <td className="px-2 py-1 text-right">
                {m.jobkoreaSampledCapped}
              </td>
              <td className="px-2 py-1 text-right">{m.jobkoreaCrawled}</td>
              <td className="px-2 py-1 text-right">{m.saraminCrawled}</td>
              <td className="px-2 py-1 text-right">{m.wantedSampled}</td>
              <td className="px-2 py-1 text-right">{m.wantedSampledCapped}</td>
              <td className="px-2 py-1 text-right">{m.wantedCrawled}</td>
              {FAMILIES.map((f) =>
                f === "OTHER" ? (
                  <td key={f} className="px-2 py-1 text-right">
                    {m.sampledByFamily[f] ?? 0}
                  </td>
                ) : (
                  <Cell
                    key={f}
                    value={m.sampledByFamily[f] ?? 0}
                    thin={isThinFamily(m.sampledByFamily[f] ?? 0)}
                  />
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
