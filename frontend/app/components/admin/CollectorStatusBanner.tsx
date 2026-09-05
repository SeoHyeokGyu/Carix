import type { JobStatusResponse } from "../../lib/collectorApi";

interface CollectorStatusBannerProps {
  activeJob: JobStatusResponse | null;
  hasAdminKey: boolean;
  isLoading: boolean;
  onRefresh: () => void;
  onOpenKeyModal: () => void;
}

export function CollectorStatusBanner({
  activeJob,
  hasAdminKey,
  isLoading,
  onRefresh,
  onOpenKeyModal,
}: CollectorStatusBannerProps) {
  return (
    <div className="flex flex-col gap-4 rounded-xl border border-carix-border bg-carix-surface p-5 shadow-xs sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-carix-info/10 text-carix-info font-bold">
          ⚡
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-carix-text">
              Collector 제어 콘솔
            </h2>
            <span className="rounded-full bg-carix-border/60 px-2 py-0.5 text-[11px] font-medium text-carix-text/70">
              Internal Pipeline
            </span>
          </div>
          <p className="text-xs text-carix-text/60 mt-0.5">
            채용공고 스크래핑, 임금 통계 연계, 중복제거 및 시장 데이터 사전 집계
            관리
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2.5">
        {/* 파이프라인 가동 락 상태 인디케이터 */}
        {activeJob ? (
          <div className="flex items-center gap-2 rounded-full border border-carix-info/30 bg-carix-info/10 px-3 py-1 text-xs font-medium text-carix-info animate-pulse">
            <span className="h-2 w-2 rounded-full bg-carix-info" />
            <span>작업 진행 중: {activeJob.jobType}</span>
          </div>
        ) : (
          <div className="flex items-center gap-2 rounded-full border border-carix-positive/30 bg-carix-positive/10 px-3 py-1 text-xs font-medium text-carix-positive">
            <span className="h-2 w-2 rounded-full bg-carix-positive" />
            <span>시스템 유휴 (IDLE)</span>
          </div>
        )}

        {/* 관리자 키 상태 */}
        <button
          type="button"
          onClick={onOpenKeyModal}
          className={`flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors ${
            hasAdminKey
              ? "border-carix-positive/40 bg-carix-positive/5 text-carix-positive hover:bg-carix-positive/10"
              : "border-carix-warning/40 bg-carix-warning/10 text-carix-warning hover:bg-carix-warning/20"
          }`}
        >
          <span>{hasAdminKey ? "🔑 키 등록됨" : "⚠️ 키 미등록"}</span>
        </button>

        {/* 새로고침 버튼 */}
        <button
          type="button"
          onClick={onRefresh}
          disabled={isLoading}
          className="flex items-center gap-1.5 rounded-lg border border-carix-border bg-carix-bg px-3 py-1.5 text-xs font-medium text-carix-text/80 hover:bg-carix-border/40 transition-colors disabled:opacity-50"
        >
          <span className={`inline-block ${isLoading ? "animate-spin" : ""}`}>
            🔄
          </span>
          <span>새로고침</span>
        </button>
      </div>
    </div>
  );
}
