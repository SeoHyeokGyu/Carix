import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingState } from "@/components/ui/loading-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TextLink } from "@/components/ui/text-link";
import { ToneBadge } from "@/components/ui/tone-badge";

import type { JobStatusResponse } from "../../lib/collectorApi";
import { crawlJobDataHref } from "../../lib/dataExplore";
import { jobTypeLabel, resolveJobStatus } from "../../lib/jobStatus";

const FILTERS = [
  { id: "ALL", label: "전체" },
  { id: "RUNNING", label: "실행 중" },
  { id: "SUCCESS", label: "성공" },
  { id: "FAILED", label: "실패" },
] as const;

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
    <div className="space-y-3">
      <div
        role="group"
        aria-label="상태 필터"
        className="flex flex-wrap items-center gap-1.5"
      >
        {FILTERS.map((f) => (
          <Button
            key={f.id}
            type="button"
            size="xs"
            variant={filter === f.id ? "default" : "outline"}
            aria-pressed={filter === f.id}
            onClick={() => setFilter(f.id)}
          >
            {f.label}
          </Button>
        ))}
      </div>
      <p className="text-xs text-carix-text/60">
        최근 실행된 비동기 파이프라인 배치 작업 (최대 100건)
      </p>

      {isLoading && jobs.length === 0 ? (
        <LoadingState message="이력을 불러오는 중입니다..." />
      ) : filteredJobs.length === 0 ? (
        <EmptyState icon="📋" title="실행 이력이 없습니다." />
      ) : (
        <Table className="text-xs">
          <TableHeader>
            <TableRow>
              <TableHead>작업 ID</TableHead>
              <TableHead>유형</TableHead>
              <TableHead>상태</TableHead>
              <TableHead>시작 시각</TableHead>
              <TableHead>소요 시간</TableHead>
              <TableHead>결과 상세</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredJobs.map((job) => {
              const status = resolveJobStatus(job.status);
              const href = crawlJobDataHref(job);
              return (
                <TableRow key={job.jobId}>
                  <TableCell
                    className="font-mono text-[11px] text-carix-text/70"
                    title={job.jobId}
                  >
                    {job.jobId.slice(0, 8)}...
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary">
                      {jobTypeLabel(job.jobType)}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <ToneBadge tone={status.tone}>{status.label}</ToneBadge>
                  </TableCell>
                  <TableCell className="whitespace-nowrap text-carix-text/70">
                    {formatTime(job.startedAt)}
                  </TableCell>
                  <TableCell className="whitespace-nowrap text-carix-text/70">
                    {formatDuration(job.startedAt, job.completedAt)}
                  </TableCell>
                  <TableCell>
                    {job.status === "FAILED" ? (
                      <span className="text-[11px] font-medium text-carix-danger">
                        {job.errorMessage ?? "원인 미상 오류"}
                      </span>
                    ) : (
                      renderDetails(job.details)
                    )}
                    {href && (
                      <div className="mt-1">
                        <TextLink to={href} direction="none" size="inherit">
                          이 달 신규 공고 보기
                        </TextLink>
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
