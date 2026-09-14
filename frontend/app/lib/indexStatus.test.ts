import { describe, expect, it } from "vitest";
import { resolveIndexStatus } from "./indexStatus";
import type { IndexStatus } from "./api/dashboard";

describe("resolveIndexStatus", () => {
  it("OK 는 뱃지를 만들지 않는다", () => {
    expect(resolveIndexStatus("OK")).toBeNull();
  });

  it("설계 §7.5 표의 문구를 그대로 돌려준다", () => {
    expect(resolveIndexStatus("INSUFFICIENT_SAMPLE")?.label).toBe("표본 부족");
    expect(resolveIndexStatus("UPDATE_DELAYED")?.label).toBe("업데이트 지연");
    expect(resolveIndexStatus("INSUFFICIENT_HISTORY")?.label).toBe("계산 대기");
    expect(resolveIndexStatus("NOT_AVAILABLE")?.label).toBe("계산 대기");
  });

  it("표본 부족과 업데이트 지연은 서로 다른 톤이다", () => {
    expect(resolveIndexStatus("INSUFFICIENT_SAMPLE")?.tone).toBe("warning");
    expect(resolveIndexStatus("UPDATE_DELAYED")?.tone).toBe("info");
  });

  it("모르는 코드는 던지지 않고 muted 로 받는다", () => {
    expect(resolveIndexStatus("SAMPLE_TOO_SMALL" as IndexStatus)).toEqual({
      label: "SAMPLE_TOO_SMALL",
      tone: "muted",
    });
  });

  // 서버 IndexContractVocabularyTest 에 대응하는 프런트 계약 테스트.
  // 어휘가 늘면 여기가 먼저 깨져서 해석 표를 같이 늘리게 만든다.
  it("IndexStatus 어휘 5종을 빠짐없이 덮는다", () => {
    const vocabulary: IndexStatus[] = [
      "OK",
      "INSUFFICIENT_SAMPLE",
      "UPDATE_DELAYED",
      "INSUFFICIENT_HISTORY",
      "NOT_AVAILABLE",
    ];
    for (const code of vocabulary) {
      expect(() => resolveIndexStatus(code)).not.toThrow();
    }
    expect(resolveIndexStatus("OK")).toBeNull();
    for (const code of vocabulary.filter((c) => c !== "OK")) {
      expect(resolveIndexStatus(code)).not.toBeNull();
    }
  });
});
