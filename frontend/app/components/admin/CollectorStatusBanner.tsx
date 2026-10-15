import type { JobStatusResponse } from "../../lib/collectorApi";
import { Button } from "@/components/ui/button";
import { ToneBadge } from "@/components/ui/tone-badge";

interface CollectorStatusBannerProps {
  activeJob: JobStatusResponse | null;
  hasAdminKey: boolean;
  onOpenKeyModal: () => void;
}

/** 제목 아래 한 줄 — 파이프라인 가동 상태와 관리자 키 상태. */
export function CollectorStatusBanner({
  activeJob,
  hasAdminKey,
  onOpenKeyModal,
}: CollectorStatusBannerProps) {
  return (
    <div className="flex flex-wrap items-center gap-2.5">
      {activeJob ? (
        <ToneBadge tone="info">작업 진행 중: {activeJob.jobType}</ToneBadge>
      ) : (
        <ToneBadge tone="positive">시스템 유휴 (IDLE)</ToneBadge>
      )}
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={onOpenKeyModal}
      >
        {hasAdminKey ? "🔑 키 등록됨" : "⚠️ 키 미등록"}
      </Button>
    </div>
  );
}
