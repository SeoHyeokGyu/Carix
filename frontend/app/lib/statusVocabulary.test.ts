import { describe, expect, it } from "vitest";
import type { Tone } from "@/components/ui/tone-badge";
import type { IndexStatus } from "./api/dashboard";
import { resolveIndexStatus } from "./indexStatus";
import {
  resolveSkillDemandStatus,
  SKILL_DEMAND_STATUS_CODES,
} from "./skillDemandStatus";
import { resolveDecisionSignal } from "./decisionSignal";

/**
 * 같은 문구가 화면에서 두 가지 색으로 나오는 것을 막는다.
 *
 * Task 2 리뷰에서 `표본 부족` 이 `indexStatus.ts` 에서는 `warning`, `skillDemandStatus.ts`
 * 에서는 `danger` 로 서로 다르게 나오는 어긋남이 드러났다(둘 다 `warning` 으로 통일).
 * 이 테스트는 그 재발 방지망이다 — 해석 모듈이 늘어나도 손으로 라벨 목록을 유지하지
 * 않도록, 각 리졸버를 자기 어휘 전체에 돌려 (라벨, 톤) 쌍을 직접 모은다.
 */

const INDEX_STATUS_CODES: IndexStatus[] = [
  "OK",
  "INSUFFICIENT_SAMPLE",
  "UPDATE_DELAYED",
  "INSUFFICIENT_HISTORY",
  "NOT_AVAILABLE",
];

const DECISION_SIGNAL_CODES = [
  "SEEK_JOB",
  "NEGOTIATE",
  "UPSKILL",
  "STAY",
  "INSUFFICIENT_DATA",
];

function collectLabelTonePairs(): { label: string; tone: Tone }[] {
  const pairs: { label: string; tone: Tone }[] = [];

  for (const code of INDEX_STATUS_CODES) {
    const resolved = resolveIndexStatus(code);
    if (resolved) pairs.push(resolved);
  }

  for (const code of SKILL_DEMAND_STATUS_CODES) {
    const resolved = resolveSkillDemandStatus(code);
    if (resolved) pairs.push(resolved);
  }

  for (const code of DECISION_SIGNAL_CODES) {
    const resolved = resolveDecisionSignal(code);
    pairs.push({ label: resolved.label, tone: resolved.tone });
  }

  return pairs;
}

describe("상태 어휘 톤 불변식", () => {
  it("같은 라벨은 어느 리졸버에서 나와도 같은 톤이다", () => {
    const tonesByLabel = new Map<string, Set<Tone>>();

    for (const { label, tone } of collectLabelTonePairs()) {
      const tones = tonesByLabel.get(label) ?? new Set<Tone>();
      tones.add(tone);
      tonesByLabel.set(label, tones);
    }

    const collisions = [...tonesByLabel.entries()]
      .filter(([, tones]) => tones.size > 1)
      .map(([label, tones]) => `"${label}" → ${[...tones].join(" vs ")}`);

    expect(
      collisions,
      collisions.length > 0
        ? `같은 라벨이 서로 다른 톤을 쓴다:\n${collisions.join("\n")}`
        : undefined,
    ).toEqual([]);
  });
});
