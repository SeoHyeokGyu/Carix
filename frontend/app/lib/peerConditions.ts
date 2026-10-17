/**
 * `S-10` 3층의 「나와 비교한 사람들」.
 *
 * 서버 `appliedConditions` 가 실제 조회 조건이라 그대로 쓴다. 시장 집계는 직무 × 경력으로만 나뉘어
 * 지역·회사규모·고용형태가 비교에 들어가지 않는다 — 프로필로 칩을 다시 만들면 집계에 없는 조건을
 * 비교한 것처럼 보인다(기능 명세 §4.2).
 */
import type { PeerGroupMetadata } from "./api/dashboard";
import { formatAppliedConditions } from "./sourceLabel";

export interface PeerConditionView {
  text: string;
  /** 본인 경력 구간에서 표본이 모자라 인접 구간까지 넓혔는가. 단계를 모르면 넓힌 것으로 본다. */
  widened: boolean;
}

const OWN_TIER_LEVEL = 1;

export function resolvePeerConditions(
  peerGroup: PeerGroupMetadata | null | undefined,
): PeerConditionView | null {
  if (!peerGroup) return null;
  return {
    text: formatAppliedConditions(peerGroup.appliedConditions),
    widened: peerGroup.fallbackLevel !== OWN_TIER_LEVEL,
  };
}
