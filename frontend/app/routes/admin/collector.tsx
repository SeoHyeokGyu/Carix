import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router";
import {
  fetchPipelineStats,
  fetchRecentJobs,
  fetchRecentPostings,
  getAdminKey,
  type PipelineStatsResponse,
  type JobStatusResponse,
  type RecentPostingResponse,
  type JobExecutionResponse,
} from "../../lib/collectorApi";
import { ThemeToggle } from "../../components/ThemeToggle";
import { AdminKeyModal } from "../../components/admin/AdminKeyModal";
import { CollectorStatusBanner } from "../../components/admin/CollectorStatusBanner";
import { CollectorStatsCards } from "../../components/admin/CollectorStatsCards";
import { PipelineControlPanel } from "../../components/admin/PipelineControlPanel";
import { JobHistoryTable } from "../../components/admin/JobHistoryTable";
import { RecentPostingsTable } from "../../components/admin/RecentPostingsTable";

export function meta() {
  return [
    { title: "Collector 관리자 콘솔 | Carix" },
    {
      name: "description",
      content: "Carix 데이터 수집 파이프라인 제어 및 모니터링 관리자 화면",
    },
  ];
}

export default function CollectorAdminPage() {
  const [stats, setStats] = useState<PipelineStatsResponse | null>(null);
  const [jobs, setJobs] = useState<JobStatusResponse[]>([]);
  const [recentPostings, setRecentPostings] = useState<RecentPostingResponse[]>(
    [],
  );
  const [isLoading, setIsLoading] = useState(false);
  const [hasAdminKey, setHasAdminKey] = useState(() => Boolean(getAdminKey()));
  const [isKeyModalOpen, setIsKeyModalOpen] = useState(false);
  const [notice, setNotice] = useState<{
    type: "success" | "error" | "info";
    text: string;
  } | null>(null);

  const activeJob = jobs.find((j) => j.status === "RUNNING") ?? null;
  const isJobRunning = Boolean(activeJob);

  const loadData = useCallback(async (isSilent = false) => {
    if (!isSilent) setIsLoading(true);
    try {
      const [statsData, jobsData, postingsData] = await Promise.allSettled([
        fetchPipelineStats(),
        fetchRecentJobs(),
        fetchRecentPostings(20),
      ]);

      if (statsData.status === "fulfilled") {
        setStats(statsData.value);
      }
      if (jobsData.status === "fulfilled") {
        setJobs(jobsData.value);
      }
      if (postingsData.status === "fulfilled") {
        setRecentPostings(postingsData.value);
      }
    } catch {
      // ignore settled errors
    } finally {
      if (!isSilent) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadData();
  }, [loadData]);

  // 진행 중인 작업이 있으면 3초 주기로 자동 폴링
  useEffect(() => {
    if (!isJobRunning) return;

    const interval = setInterval(async () => {
      try {
        const [statsData, jobsData, postingsData] = await Promise.allSettled([
          fetchPipelineStats(),
          fetchRecentJobs(),
          fetchRecentPostings(20),
        ]);

        if (statsData.status === "fulfilled") setStats(statsData.value);
        if (postingsData.status === "fulfilled")
          setRecentPostings(postingsData.value);
        if (jobsData.status === "fulfilled") {
          const latestJobs = jobsData.value;
          setJobs(latestJobs);
          const stillRunning = latestJobs.some((j) => j.status === "RUNNING");
          if (!stillRunning) {
            const finished = latestJobs[0];
            if (finished?.status === "SUCCESS") {
              setNotice({
                type: "success",
                text: `작업 [${finished.jobType}]이(가) 성공적으로 완료되었습니다.`,
              });
            } else if (finished?.status === "FAILED") {
              setNotice({
                type: "error",
                text: `작업 [${finished.jobType}] 실행 중 오류가 발생했습니다: ${finished.errorMessage ?? ""}`,
              });
            }
          }
        }
      } catch {
        // ignore polling errors
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [isJobRunning]);

  const handleJobTriggered = (res: JobExecutionResponse) => {
    setNotice({
      type: "info",
      text: `새 작업 [${res.jobType}]이(가) 백그라운드에서 시작되었습니다. (ID: ${res.jobId.slice(0, 8)})`,
    });
    loadData(true);
  };

  const handleError = (err: { message: string }) => {
    setNotice({
      type: "error",
      text: err.message,
    });
  };

  return (
    <div className="min-h-screen bg-carix-bg text-carix-text transition-colors duration-300">
      <ThemeToggle />

      {/* 상단 통합 네비게이션 */}
      <header className="border-b border-carix-border bg-carix-surface/80 backdrop-blur-md sticky top-0 z-40">
        <div className="mx-auto flex w-full max-w-7xl items-center justify-between px-6 py-3">
          <div className="flex items-center gap-3">
            <Link
              to="/"
              className="font-bold text-base tracking-tight text-carix-text hover:text-carix-info transition-colors"
            >
              Carix
            </Link>
            <span className="text-carix-border font-light">/</span>
            <span className="text-sm font-semibold text-carix-info">
              Admin &middot; Collector Pipeline
            </span>
          </div>

          <div className="flex items-center gap-2 text-xs">
            <Link
              to="/dashboard"
              className="rounded-md px-3 py-1.5 text-carix-text/70 hover:bg-carix-border/40 hover:text-carix-text transition-colors"
            >
              사용자 대시보드
            </Link>
          </div>
        </div>
      </header>

      {/* 본문 콘텐츠 */}
      <main className="mx-auto max-w-7xl px-6 py-8 space-y-6">
        {/* 알림 배너 */}
        {notice && (
          <div
            className={`flex items-center justify-between rounded-lg p-4 text-xs font-medium border ${
              notice.type === "success"
                ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-700 dark:text-emerald-300"
                : notice.type === "error"
                  ? "bg-rose-500/10 border-rose-500/30 text-rose-700 dark:text-rose-300"
                  : "bg-blue-500/10 border-blue-500/30 text-blue-700 dark:text-blue-300"
            }`}
          >
            <span>{notice.text}</span>
            <button
              type="button"
              onClick={() => setNotice(null)}
              className="opacity-70 hover:opacity-100 ml-4 font-bold"
            >
              ✕
            </button>
          </div>
        )}

        {/* 1. 상단 상태 및 락 인디케이터 배너 */}
        <CollectorStatusBanner
          activeJob={activeJob}
          hasAdminKey={hasAdminKey}
          isLoading={isLoading}
          onRefresh={() => loadData(false)}
          onOpenKeyModal={() => setIsKeyModalOpen(true)}
        />

        {/* 2. 수집 DB 현황 요약 카드 */}
        <section>
          <CollectorStatsCards stats={stats} isLoading={isLoading} />
        </section>

        {/* 3. 파이프라인 제어 패널 */}
        <section>
          <PipelineControlPanel
            isJobRunning={isJobRunning}
            onJobTriggered={handleJobTriggered}
            onError={handleError}
          />
        </section>

        {/* 4. 작업 실행 이력 테이블 */}
        <section>
          <JobHistoryTable jobs={jobs} isLoading={isLoading} />
        </section>

        {/* 5. 최근 수집 공고 미리보기 테이블 */}
        <section>
          <RecentPostingsTable
            postings={recentPostings}
            isLoading={isLoading}
          />
        </section>
      </main>

      {/* 관리자 키 설정 모달 */}
      <AdminKeyModal
        isOpen={isKeyModalOpen}
        onClose={() => setIsKeyModalOpen(false)}
        onKeyUpdated={() => setHasAdminKey(Boolean(getAdminKey()))}
      />
    </div>
  );
}
