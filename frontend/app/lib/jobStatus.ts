import type { Tone } from "@/components/ui/tone-badge";

/** 수집 작업 상태 → 문구·톤. 모르는 상태는 코드 그대로 muted 로 보인다. */
const JOB_STATUS: Record<string, { label: string; tone: Tone }> = {
  RUNNING: { label: "실행 중", tone: "info" },
  SUCCESS: { label: "성공", tone: "positive" },
  FAILED: { label: "실패", tone: "danger" },
};

/** 이 모듈이 아는 작업 상태 코드 — 톤 충돌 검사(`statusVocabulary.test.ts`)가 돈다. */
export const JOB_STATUS_CODES = Object.keys(JOB_STATUS);

export function resolveJobStatus(status: string): {
  label: string;
  tone: Tone;
} {
  return JOB_STATUS[status] ?? { label: status, tone: "muted" };
}

const JOB_TYPE_LABELS: Record<string, string> = {
  CRAWL: "크롤링",
  WAGE_STAT_INGEST: "임금 통계",
  CPI_INGEST: "소비자물가지수",
  DEDUPLICATION: "중복제거",
  MARKET_AGGREGATION: "시장 집계",
  SKILL_REFINE: "스킬 재정제",
  SEED_LOAD: "시드 적재",
};

/** 작업 유형 코드 → 사람이 읽는 이름. 모르는 유형은 코드 그대로. */
export function jobTypeLabel(type: string): string {
  return JOB_TYPE_LABELS[type] ?? type;
}

/** 파이프라인 가동 상태 — 진행 중인 작업이 있으면 그 이름, 없으면 유휴. */
export function resolvePipelineState(activeJobType: string | null): {
  label: string;
  tone: Tone;
} {
  return activeJobType
    ? {
        label: `작업 진행 중: ${jobTypeLabel(activeJobType)}`,
        tone: JOB_STATUS.RUNNING.tone,
      }
    : { label: "시스템 유휴 (IDLE)", tone: "positive" };
}

/** 관리자 키 상태. 키가 없으면 실행·조회가 다 막히므로 경고다. */
export function resolveAdminKeyState(hasKey: boolean): {
  label: string;
  tone: Tone;
} {
  return hasKey
    ? { label: "키 등록됨", tone: "positive" }
    : { label: "키 미등록", tone: "warning" };
}
