import type { PipelineStatsResponse } from "../../lib/collectorApi";
import { sourceLabel } from "../../lib/sourceLabel";
import { Card } from "@/components/ui/card";
import { LoadingState } from "@/components/ui/loading-state";

/** 원천 표시 순서. 서버가 이 밖의 원천을 보내면 뒤에 붙인다. */
const SOURCE_ORDER = ["JOBKOREA", "WANTED", "SARAMIN"];

const SOURCE_COLORS: Record<string, string> = {
  JOBKOREA: "bg-carix-info",
  WANTED: "bg-carix-positive",
  SARAMIN: "bg-carix-warning",
};
const FALLBACK_COLOR = "bg-carix-text/40";

interface CollectorStatsCardsProps {
  stats: PipelineStatsResponse | null;
  isLoading: boolean;
}

export function CollectorStatsCards({
  stats,
  isLoading,
}: CollectorStatsCardsProps) {
  if (isLoading && !stats) {
    return <LoadingState message="수집 현황을 불러오는 중입니다..." />;
  }

  const rawCount = stats?.totalRawIngest ?? 0;
  const postingCount = stats?.totalJobPostings ?? 0;
  const skillCount = stats?.totalSkills ?? 0;
  const wageCount = stats?.totalWageStats ?? 0;

  const bySource = stats?.postingsBySource ?? {};
  const sourceCodes = [
    ...SOURCE_ORDER.filter((code) => code in bySource),
    ...Object.keys(bySource).filter((code) => !SOURCE_ORDER.includes(code)),
  ];
  const portals = sourceCodes
    .map((code) => ({ code, count: bySource[code] ?? 0 }))
    .filter((p) => p.count > 0);
  const totalPortal = portals.reduce((sum, p) => sum + p.count, 0);
  const pct = (count: number) =>
    totalPortal > 0 ? Math.round((count / totalPortal) * 100) : 0;

  const cards = [
    {
      title: "원문 수집 (Raw Ingest)",
      count: rawCount.toLocaleString(),
      subtext: "core.raw_ingest 보존 건수",
      icon: "📥",
    },
    {
      title: "정규화 채용공고",
      count: postingCount.toLocaleString(),
      subtext: "core.job_posting 적재 건수",
      icon: "📄",
    },
    {
      title: "표준 스킬 사전",
      count: skillCount.toLocaleString(),
      subtext: "core.skill 등록/추출 스킬",
      icon: "🏷️",
    },
    {
      title: "KOSIS 공식 임금 통계",
      count: wageCount.toLocaleString(),
      subtext: "core.wage_stat_observation",
      icon: "📊",
    },
  ];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {cards.map((c) => (
          <Card key={c.title} className="gap-0 p-4.5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-carix-text/70">
                {c.title}
              </span>
              <span className="text-lg">{c.icon}</span>
            </div>
            <div className="mt-2 text-2xl font-bold text-carix-text tracking-tight">
              {c.count}{" "}
              <span className="text-xs font-normal text-carix-text/60">건</span>
            </div>
            <p className="mt-1 text-[11px] text-carix-text/50">{c.subtext}</p>
          </Card>
        ))}
      </div>

      {/* 포털별 수집 비율 바 */}
      <Card className="gap-0 p-4">
        <div className="flex items-center justify-between text-xs font-medium text-carix-text mb-2">
          <span>원천 채용 포털별 공고 분포</span>
          <span className="text-carix-text/60">
            총 {totalPortal.toLocaleString()}건
          </span>
        </div>

        <p className="mb-2 text-[11px] text-carix-text/50">
          누적 공고 수 · 점핏 공고는 사람인에 포함
        </p>
        <div className="h-3 w-full rounded-full overflow-hidden flex bg-carix-border/40">
          {portals.map((p) => (
            <div
              key={p.code}
              style={{ width: `${pct(p.count)}%` }}
              className={`${SOURCE_COLORS[p.code] ?? FALLBACK_COLOR} transition-all duration-500`}
              title={`${sourceLabel(p.code)}: ${p.count.toLocaleString()}건 (${pct(p.count)}%)`}
            />
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-carix-text/70 mt-2">
          {portals.map((p) => (
            <div key={p.code} className="flex items-center gap-1.5">
              <span
                className={`h-2.5 w-2.5 rounded-full ${SOURCE_COLORS[p.code] ?? FALLBACK_COLOR}`}
              />
              <span>
                {sourceLabel(p.code)} {p.count.toLocaleString()}건 (
                {pct(p.count)}%)
              </span>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
