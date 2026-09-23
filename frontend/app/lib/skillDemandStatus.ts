import type { Tone } from "@/components/ui/tone-badge";

/**
 * 스킬 수요 상태의 화면 표현.
 *
 * 서버 어휘는 두 출처가 섞여 있다 — `com.carix.index.enums.SkillDemandStatus` 4종과,
 * 커스텀 스킬을 표시하려고 `MarketValueIndexServiceImpl` 이 직접 넣는 문자열
 * `CUSTOM_SKILL_NO_STATS` 하나다. 서버 DTO 의 필드 타입이 `String` 이라 enum 으로 좁힐 수
 * 없어, 프런트에서 유니온으로 못박고 계약 테스트로 지킨다.
 *
 * 지수 상태(`lib/indexStatus.ts`)와 합치지 않는다. 서버에서 다른 enum 이다.
 *
 * NOTE: (cjkang) `INSUFFICIENT_SAMPLE` 과 `NO_RECENT_DEMAND` 는 서버 집계가 현재
 * 버리고 있어 실제로는 내려오지 않는다. 표에 미리 넣어 둔 것은 서버가 고쳐질 때
 * 프런트를 다시 건드리지 않기 위해서다 (스펙 `ui-unification-plan.md` §P2).
 */
export const SKILL_DEMAND_STATUS_CODES = [
  "OK",
  "NEW_OBSERVATION",
  "INSUFFICIENT_SAMPLE",
  "NO_RECENT_DEMAND",
  "NO_RECENT_COVERAGE",
  "CUSTOM_SKILL_NO_STATS",
] as const;

export type SkillDemandStatusCode = (typeof SKILL_DEMAND_STATUS_CODES)[number];

const STATUS: Record<
  Exclude<SkillDemandStatusCode, "OK">,
  { label: string; tone: Tone }
> = {
  CUSTOM_SKILL_NO_STATS: { label: "통계 미제공", tone: "warning" },
  NEW_OBSERVATION: { label: "신규 관측", tone: "info" },
  // `indexStatus.ts` 의 같은 문구와 톤을 맞춘다 — 같은 라벨이면 같은 색이다.
  INSUFFICIENT_SAMPLE: { label: "표본 부족", tone: "warning" },
  NO_RECENT_DEMAND: { label: "최근 수요 없음", tone: "muted" },
  // 표본 부족과 갈라 둔다 — 사용자가 스킬을 보강해도 해소되지 않는 구간이다.
  NO_RECENT_COVERAGE: { label: "집계 없음", tone: "warning" },
};

/** `OK` 만 `null` 이다 — 그 자리에는 수요 추세 뱃지가 들어간다. */
export function resolveSkillDemandStatus(
  status: string,
): { label: string; tone: Tone } | null {
  if (status === "OK") return null;
  const known = STATUS[status as Exclude<SkillDemandStatusCode, "OK">];
  return known ?? { label: status, tone: "muted" };
}

/** 기준선 100 대비 수요 지수의 추세. 임계값은 기능 명세의 ±5p 구간이다. */
const TREND_UP_THRESHOLD = 105;
const TREND_DOWN_THRESHOLD = 95;

export function resolveDemandTrend(demandIndex: number): {
  label: string;
  tone: Tone;
} {
  if (demandIndex >= TREND_UP_THRESHOLD)
    return { label: "▲ 상승", tone: "positive" };
  if (demandIndex <= TREND_DOWN_THRESHOLD)
    return { label: "▼ 하락", tone: "warning" };
  return { label: "- 유지", tone: "info" };
}
