import { ToneBadge } from "@/components/ui/tone-badge";

import type { JobStatusResponse } from "../../lib/collectorApi";
import {
  resolveAdminKeyState,
  resolvePipelineState,
} from "../../lib/jobStatus";

interface CollectorStatusBannerProps {
  activeJob: JobStatusResponse | null;
  hasAdminKey: boolean;
}

/** 제목 아래 한 줄 — 파이프라인 가동 상태와 관리자 키 상태. 키 창은 헤더가 연다. */
export function CollectorStatusBanner({
  activeJob,
  hasAdminKey,
}: CollectorStatusBannerProps) {
  const pipeline = resolvePipelineState(activeJob?.jobType ?? null);
  const key = resolveAdminKeyState(hasAdminKey);
  return (
    <div className="flex flex-wrap items-center gap-2.5">
      <ToneBadge tone={pipeline.tone}>{pipeline.label}</ToneBadge>
      <ToneBadge tone={key.tone}>{key.label}</ToneBadge>
    </div>
  );
}
