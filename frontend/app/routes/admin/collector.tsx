import { useState, useEffect, useCallback } from "react";
import {
  fetchPipelineStats,
  fetchRecentJobs,
  getAdminKey,
  type PipelineStatsResponse,
  type JobStatusResponse,
  type JobExecutionResponse,
} from "../../lib/collectorApi";
import { X } from "lucide-react";
import { ThemeToggle } from "../../components/ThemeToggle";
import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { InlineAlert } from "@/components/ui/inline-alert";
import { AdminHeader } from "../../components/admin/AdminHeader";
import { AdminKeyModal } from "../../components/admin/AdminKeyModal";
import { CollectorStatusBanner } from "../../components/admin/CollectorStatusBanner";
import { CollectorStatsCards } from "../../components/admin/CollectorStatsCards";
import { PipelineControlPanel } from "../../components/admin/PipelineControlPanel";
import { JobHistoryTable } from "../../components/admin/JobHistoryTable";

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
  // 첫 렌더부터 불러오는 중이다 — false 로 시작하면 한 프레임 「이력 없음」이 비친다.
  const [isLoading, setIsLoading] = useState(true);
  const [statsFailed, setStatsFailed] = useState(false);
  const [jobsFailed, setJobsFailed] = useState(false);
  const [hasAdminKey, setHasAdminKey] = useState(() => Boolean(getAdminKey()));
  const [isKeyModalOpen, setIsKeyModalOpen] = useState(false);
  const [notice, setNotice] = useState<{
    type: "success" | "error" | "info";
    text: string;
  } | null>(null);

  const activeJob = jobs.find((j) => j.status === "RUNNING") ?? null;
  const isJobRunning = Boolean(activeJob);

  const loadData = useCallback(async (isSilent = false) => {
    if (!isSilent) {
      setIsLoading(true);
      // 다시 시도하는 동안 실패 박스 대신 로딩을 보인다 — 연타로 요청이 겹치지 않게.
      setStatsFailed(false);
      setJobsFailed(false);
    }
    try {
      const [statsData, jobsData] = await Promise.allSettled([
        fetchPipelineStats(),
        fetchRecentJobs(),
      ]);

      // 받아 둔 값이 있으면 실패해도 화면을 비우지 않는다 — 실패 표시는 값이 없을 때만 쓴다.
      setStatsFailed(statsData.status === "rejected");
      if (statsData.status === "fulfilled") {
        setStats(statsData.value);
      }
      setJobsFailed(jobsData.status === "rejected");
      if (jobsData.status === "fulfilled") {
        setJobs(jobsData.value);
      }
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
        const [statsData, jobsData] = await Promise.allSettled([
          fetchPipelineStats(),
          fetchRecentJobs(),
        ]);

        if (statsData.status === "fulfilled") setStats(statsData.value);
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

  const retry = () => void loadData(false);

  return (
    <div className="min-h-screen bg-carix-bg text-carix-text transition-colors duration-300">
      <ThemeToggle />
      <AdminHeader active="collector" />

      <PageFrame as="main" width="admin" aria-busy={isLoading}>
        <PageHeader
          title="수집 파이프라인"
          description="채용공고 스크래핑, 임금 통계 연계, 중복제거 및 시장 데이터 사전 집계를 실행하고 지켜봅니다."
          divider
        />

        <div className="mb-8 space-y-3">
          <CollectorStatusBanner
            activeJob={activeJob}
            hasAdminKey={hasAdminKey}
            onOpenKeyModal={() => setIsKeyModalOpen(true)}
          />
          {notice?.type === "error" && (
            <InlineAlert
              message={notice.text}
              onDismiss={() => setNotice(null)}
            />
          )}
          {notice && notice.type !== "error" && (
            <div role="status" className="flex flex-wrap items-center gap-3">
              <p
                className={`text-sm ${
                  notice.type === "success"
                    ? "text-carix-positive"
                    : "text-carix-info"
                }`}
              >
                {notice.text}
              </p>
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                aria-label="알림 닫기"
                onClick={() => setNotice(null)}
              >
                <X />
              </Button>
            </div>
          )}
        </div>

        <PageSection title="수집 현황">
          {statsFailed && stats && (
            <div className="mb-3">
              <InlineAlert
                message="수집 현황을 다시 불러오지 못했습니다"
                onRetry={retry}
                retrying={isLoading}
              />
            </div>
          )}
          {statsFailed && !stats ? (
            <LoadFailure
              title="수집 현황을 불러오지 못했습니다"
              onRetry={retry}
            />
          ) : (
            <CollectorStatsCards stats={stats} isLoading={isLoading} />
          )}
        </PageSection>

        <PageSection title="파이프라인 실행">
          <PipelineControlPanel
            isJobRunning={isJobRunning}
            onJobTriggered={handleJobTriggered}
            onError={handleError}
          />
        </PageSection>

        <PageSection title="작업 실행 이력">
          {jobsFailed && jobs.length > 0 && (
            <div className="mb-3">
              <InlineAlert
                message="작업 실행 이력을 다시 불러오지 못했습니다"
                onRetry={retry}
                retrying={isLoading}
              />
            </div>
          )}
          {jobsFailed && jobs.length === 0 ? (
            <LoadFailure
              title="작업 실행 이력을 불러오지 못했습니다"
              onRetry={retry}
            />
          ) : (
            <JobHistoryTable jobs={jobs} isLoading={isLoading} />
          )}
        </PageSection>
      </PageFrame>

      <AdminKeyModal
        isOpen={isKeyModalOpen}
        onClose={() => setIsKeyModalOpen(false)}
        onKeyUpdated={() => {
          setHasAdminKey(Boolean(getAdminKey()));
          // 키가 바뀌면 그 키로 다시 불러온다 — 새로고침 버튼을 두지 않는다.
          void loadData(true);
        }}
      />
    </div>
  );
}

/** 구획 하나가 보여 줄 데이터 없이 실패했을 때. 키가 없거나 틀린 경우가 흔하다. */
function LoadFailure({
  title,
  onRetry,
}: {
  title: string;
  onRetry: () => void;
}) {
  return (
    <EmptyState
      tone="danger"
      icon="⚠️"
      title={title}
      description="관리자 키를 확인하거나 잠시 후 다시 시도해 주세요."
    >
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        다시 시도
      </Button>
    </EmptyState>
  );
}
