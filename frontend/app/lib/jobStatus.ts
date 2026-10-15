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
