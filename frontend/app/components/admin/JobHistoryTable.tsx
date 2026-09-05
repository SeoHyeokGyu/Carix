import { useState } from "react";
import type { JobStatusResponse } from "../../lib/collectorApi";

interface JobHistoryTableProps {
  jobs: JobStatusResponse[];
  isLoading: boolean;
}

export function JobHistoryTable({ jobs, isLoading }: JobHistoryTableProps) {
  const [filter, setFilter] = useState<string>("ALL");

  const filteredJobs = jobs.filter((job) => {
    if (filter === "ALL") return true;
    return job.status === filter;
  });

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "RUNNING":
        return (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-blue-500/10 px-2.5 py-0.5 text-xs font-semibold text-blue-600 dark:text-blue-400 animate-pulse">
            <span className="h-1.5 w-1.5 rounded-full bg-blue-500" />
            실행 중
          </span>
        );
      case "SUCCESS":
        return (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            성공
          </span>
        );
      case "FAILED":
        return (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-rose-500/10 px-2.5 py-0.5 text-xs font-semibold text-rose-600 dark:text-rose-400">
            <span className="h-1.5 w-1.5 rounded-full bg-rose-500" />
            실패
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center rounded-full bg-carix-border px-2 py-0.5 text-xs font-medium text-carix-text/70">
            {status}
          </span>
        );
    }
  };

  const getJobTypeLabel = (type: string) => {
    switch (type) {
      case "CRAWL":
        return { label: "크롤링", color: "text-blue-600 bg-blue-500/10" };
      case "WAGE_STAT_INGEST":
        return { label: "임금 통계", color: "text-amber-600 bg-amber-500/10" };
      case "DEDUPLICATION":
        return { label: "중복제거", color: "text-purple-600 bg-purple-500/10" };
      case "MARKET_AGGREGATION":
        return {
          label: "시장 집계",
          color: "text-emerald-600 bg-emerald-500/10",
        };
      case "SEED_LOAD":
        return { label: "시드 적재", color: "text-slate-600 bg-slate-500/10" };
      default:
        return { label: type, color: "text-carix-text bg-carix-border/40" };
    }
  };

  const formatDuration = (
    startedAt: string,
    completedAt?: string | null,
  ): string => {
    if (!completedAt) return "진행 중...";
    const start = new Date(startedAt).getTime();
    const end = new Date(completedAt).getTime();
    const diffSec = Math.max(0, Math.floor((end - start) / 1000));

    if (diffSec < 60) return `${diffSec}초`;
    const min = Math.floor(diffSec / 60);
    const sec = diffSec % 60;
    return `${min}분 ${sec}초`;
  };

  const formatTime = (isoString: string): string => {
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString("ko-KR", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      });
    } catch {
      return isoString;
    }
  };

  const renderDetails = (details?: Record<string, unknown> | null) => {
    if (!details || Object.keys(details).length === 0) return "-";
    return (
      <div className="flex flex-wrap gap-1">
        {Object.entries(details).map(([key, val]) => (
          <span
            key={key}
            className="inline-block rounded bg-carix-border/50 px-1.5 py-0.5 text-[11px] font-mono text-carix-text/80"
          >
            {key}: <strong>{String(val)}</strong>
          </span>
        ))}
      </div>
    );
  };

  return (
    <div className="rounded-xl border border-carix-border bg-carix-surface shadow-xs overflow-hidden">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-carix-border px-5 py-4">
        <div>
          <h3 className="text-sm font-bold text-carix-text">작업 실행 이력</h3>
          <p className="text-xs text-carix-text/60 mt-0.5">
            최근 실행된 비동기 파이프라인 배치 작업 (최대 100건)
          </p>
        </div>

        <div className="flex items-center gap-1.5">
          {["ALL", "RUNNING", "SUCCESS", "FAILED"].map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => setFilter(tab)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                filter === tab
                  ? "bg-carix-info text-white"
                  : "bg-carix-bg text-carix-text/70 hover:bg-carix-border/50"
              }`}
            >
              {tab === "ALL"
                ? "전체"
                : tab === "RUNNING"
                  ? "진행중"
                  : tab === "SUCCESS"
                    ? "성공"
                    : "실패"}
            </button>
          ))}
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs text-carix-text">
          <thead className="bg-carix-bg/60 border-b border-carix-border text-carix-text/60 uppercase font-semibold text-[11px]">
            <tr>
              <th className="px-5 py-3">작업 ID</th>
              <th className="px-4 py-3">유형</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3">시작 시각</th>
              <th className="px-4 py-3">소요 시간</th>
              <th className="px-5 py-3">결과 상세</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-carix-border">
            {isLoading && jobs.length === 0 ? (
              <tr>
                <td
                  colSpan={6}
                  className="px-5 py-8 text-center text-carix-text/50"
                >
                  이력을 불러오는 중입니다...
                </td>
              </tr>
            ) : filteredJobs.length === 0 ? (
              <tr>
                <td
                  colSpan={6}
                  className="px-5 py-8 text-center text-carix-text/50"
                >
                  실행 이력이 없습니다.
                </td>
              </tr>
            ) : (
              filteredJobs.map((job) => {
                const typeInfo = getJobTypeLabel(job.jobType);
                return (
                  <tr
                    key={job.jobId}
                    className="hover:bg-carix-border/20 transition-colors"
                  >
                    <td
                      className="px-5 py-3 font-mono text-[11px] text-carix-text/70"
                      title={job.jobId}
                    >
                      {job.jobId.slice(0, 8)}...
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${typeInfo.color}`}
                      >
                        {typeInfo.label}
                      </span>
                    </td>
                    <td className="px-4 py-3">{getStatusBadge(job.status)}</td>
                    <td className="px-4 py-3 whitespace-nowrap text-carix-text/70">
                      {formatTime(job.startedAt)}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-carix-text/70">
                      {formatDuration(job.startedAt, job.completedAt)}
                    </td>
                    <td className="px-5 py-3">
                      {job.status === "FAILED" ? (
                        <span className="text-rose-500 text-[11px] font-medium">
                          {job.errorMessage ?? "원인 미상 오류"}
                        </span>
                      ) : (
                        renderDetails(job.details)
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
