import type { Tone } from "@/components/ui/tone-badge";
import type { IndexStatus } from "./api/dashboard";

/**
 * 지수 열화 상태의 화면 표현 (설계 §7.5 표).
 *
 * `INSUFFICIENT_HISTORY` 와 `NOT_AVAILABLE` 은 사용자에게 같은 "계산 대기"로 묶인다 —
 * 원인은 다르지만 할 수 있는 일이 같아서다.
 *
 * 스킬 수요 상태(`lib/skillDemandStatus.ts`)와 합치지 않는다. 서버에서 다른 enum 이고
 * 합치면 한쪽에 없는 상태값이 다른 쪽 화면에 노출된다.
 */
const INDEX_STATUS: Record<
  Exclude<IndexStatus, "OK">,
  { label: string; tone: Tone }
> = {
  INSUFFICIENT_SAMPLE: { label: "표본 부족", tone: "warning" },
  UPDATE_DELAYED: { label: "업데이트 지연", tone: "info" },
  INSUFFICIENT_HISTORY: { label: "계산 대기", tone: "muted" },
  NOT_AVAILABLE: { label: "계산 대기", tone: "muted" },
};

/**
 * `IndexStatus` 어휘 전체 — `INDEX_STATUS` 표의 키에서 파생한다.
 * 손으로 다시 나열하지 않는다: 표가 늘면 여기도 같이 는다.
 */
export const INDEX_STATUS_CODES: IndexStatus[] = [
  "OK",
  ...(Object.keys(INDEX_STATUS) as Exclude<IndexStatus, "OK">[]),
];

/** `OK` 는 뱃지를 만들지 않는다 — 정상 상태에 라벨을 붙이지 않는다. */
export function resolveIndexStatus(
  status: IndexStatus,
): { label: string; tone: Tone } | null {
  if (status === "OK") return null;
  return INDEX_STATUS[status] ?? { label: status, tone: "muted" };
}
