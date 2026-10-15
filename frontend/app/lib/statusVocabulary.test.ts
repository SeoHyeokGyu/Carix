import { describe, expect, it } from "vitest";

import type { Tone } from "@/components/ui/tone-badge";

import { DECISION_SIGNAL_CODES, resolveDecisionSignal } from "./decisionSignal";
import { INDEX_STATUS_CODES, resolveIndexStatus } from "./indexStatus";
import { JOB_STATUS_CODES, resolveJobStatus } from "./jobStatus";
import {
  resolveSkillDemandStatus,
  SKILL_DEMAND_STATUS_CODES,
} from "./skillDemandStatus";

/**
 * 같은 문구가 화면에서 두 가지 색으로 나오는 것을 막는다.
 *
 * Task 2 리뷰에서 `표본 부족` 이 `indexStatus.ts` 에서는 `warning`, `skillDemandStatus.ts`
 * 에서는 `danger` 로 서로 다르게 나오는 어긋남이 드러났다(둘 다 `warning` 으로 통일).
 * 이 테스트는 그 재발 방지망이다 — 해석 모듈이 늘어나도 손으로 라벨 목록을 유지하지
 * 않도록, 각 리졸버를 자기 어휘 전체에 돌려 (라벨, 톤) 쌍을 직접 모은다.
 *
 * 어휘 배열(`INDEX_STATUS_CODES`, `DECISION_SIGNAL_CODES`, `SKILL_DEMAND_STATUS_CODES`)은
 * 이 파일이 아니라 각 리졸버 모듈이 자기 표에서 파생해 export 한다 — 여기서 리터럴로
 * 다시 나열하면 리졸버 표와 두 벌이 되어 어긋날 수 있다.
 */

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

  for (const code of JOB_STATUS_CODES) {
    pairs.push(resolveJobStatus(code));
  }

  return pairs;
}

/**
 * `collectLabelTonePairs` 에 실제로 연결된 리졸버 모듈 목록.
 *
 * 손으로 유지하는 목록이 맞다 — 아래 글롭 검사가 이 목록을 파생시켜 버리면
 * "새 리졸버가 빠졌는지" 를 검사할 대상 자체가 사라져 가드가 무의미해진다.
 * 새 리졸버를 `collectLabelTonePairs` 에 연결했으면 여기에도 같이 추가한다.
 */
const WIRED_RESOLVER_MODULES = [
  "./indexStatus.ts",
  "./skillDemandStatus.ts",
  "./decisionSignal.ts",
  "./jobStatus.ts",
];

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

  it("새로 추가된 리졸버 모듈이 충돌 검사에 빠짐없이 연결돼 있다", () => {
    // 코드 상태 리졸버는 관례상 `*Status.ts` 또는 `*Signal.ts` 로 끝난다
    // (`decisionSignal.ts` 는 `*Status.ts` 패턴에 걸리지 않아 두 번째 패턴이 필요하다).
    // 이 관례를 벗어난 새 리졸버는 이 글롭이 못 잡으므로, 그런 파일을 추가할 때는
    // `WIRED_RESOLVER_MODULES` 와 `collectLabelTonePairs` 에 손으로 같이 추가해야 한다.
    const discovered = import.meta.glob(["./*Status.ts", "./*Signal.ts"]);
    const discoveredModules = Object.keys(discovered);

    const unwired = discoveredModules.filter(
      (mod) => !WIRED_RESOLVER_MODULES.includes(mod),
    );
    const missing = WIRED_RESOLVER_MODULES.filter(
      (mod) => !discoveredModules.includes(mod),
    );

    const problems = [
      ...unwired.map(
        (mod) =>
          `${mod} 가 새로 생겼지만 톤 충돌 검사에 연결되지 않았다 — ` +
          `이 파일의 어휘를 export 하고, collectLabelTonePairs 와 ` +
          `WIRED_RESOLVER_MODULES 양쪽에 추가하라.`,
      ),
      ...missing.map(
        (mod) =>
          `${mod} 가 WIRED_RESOLVER_MODULES 에 있지만 실제 파일이 없다 — ` +
          `이름을 바꿨거나 지웠다면 WIRED_RESOLVER_MODULES 에서도 지워라.`,
      ),
    ];

    expect(
      problems,
      problems.length > 0 ? problems.join("\n") : undefined,
    ).toEqual([]);
  });
});
