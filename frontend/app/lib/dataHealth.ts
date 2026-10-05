import type { HealthCheck, HealthVerdict } from "./collectorApi";

export const VERDICT_LABEL: Record<HealthVerdict, string> = {
  OK: "정상",
  WARN: "주의",
  ERROR: "문제",
  UNKNOWN: "판정 불가",
};

const ORDER: Record<HealthVerdict, number> = {
  ERROR: 0,
  WARN: 1,
  UNKNOWN: 2,
  OK: 3,
};

/** 문제 → 주의 → 판정 불가 → 정상. 판정 불가를 정상과 섞지 않는다 — 숫자를 지어내지 않는다는 원칙. */
export function sortChecks(checks: HealthCheck[]): HealthCheck[] {
  return [...checks].sort((a, b) => ORDER[a.verdict] - ORDER[b.verdict]);
}

/** 빈 답 사유의 화면 문구. 모르는 사유는 코드 그대로 보인다. */
export const GAP_REASON_LABEL: Record<string, string> = {
  NO_SNAPSHOT: "스냅샷 없음",
  UNKNOWN_RATIONALE: "근거 없음(옛 스냅샷)",
  INSUFFICIENT_SAMPLE: "표본 부족",
  UPDATE_DELAYED: "업데이트 지연",
  NOT_AVAILABLE: "산출 불가",
  INSUFFICIENT_DATA: "데이터 부족",
  MVI_NOT_REFLECTED: "시장가치 미반영",
  MVI_UNCERTAIN: "시장가치 불확실",
};
