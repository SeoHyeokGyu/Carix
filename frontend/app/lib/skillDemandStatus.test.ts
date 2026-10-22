import { describe, expect, it } from "vitest";

import {
  resolveDemandTrend,
  resolveSkillDemandStatus,
  SKILL_DEMAND_STATUS_CODES,
} from "./skillDemandStatus";

describe("resolveSkillDemandStatus", () => {
  it("OK 는 상태 뱃지를 만들지 않는다 — 추세 뱃지가 그 자리를 쓴다", () => {
    expect(resolveSkillDemandStatus("OK")).toBeNull();
  });

  // 서버 IndexContractVocabularyTest 의 프런트 대응물.
  // SkillDemandStatus enum 5종 + CUSTOM_SKILL_NO_STATS 리터럴 = 6종을 덮어야 한다.
  it("서버 어휘 6종을 빠짐없이 덮는다", () => {
    expect([...SKILL_DEMAND_STATUS_CODES].sort()).toEqual(
      [
        "OK",
        "NEW_OBSERVATION",
        "INSUFFICIENT_SAMPLE",
        "NO_RECENT_DEMAND",
        "NO_RECENT_COVERAGE",
        "CUSTOM_SKILL_NO_STATS",
      ].sort(),
    );
  });
});

describe("resolveDemandTrend", () => {
  it("105 이상은 상승, 95 이하는 하락, 사이는 유지다", () => {
    expect(resolveDemandTrend(105)).toEqual({
      label: "▲ 상승",
      tone: "positive",
    });
    expect(resolveDemandTrend(95)).toEqual({
      label: "▼ 하락",
      tone: "warning",
    });
    expect(resolveDemandTrend(100)).toEqual({ label: "- 유지", tone: "info" });
  });

  it("경계값은 상승·하락 쪽에 붙는다", () => {
    expect(resolveDemandTrend(104.9).tone).toBe("info");
    expect(resolveDemandTrend(95.1).tone).toBe("info");
  });
});
