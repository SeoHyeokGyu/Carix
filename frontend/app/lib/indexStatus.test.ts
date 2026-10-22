import { describe, expect, it } from "vitest";

import type { IndexStatus } from "./api/dashboard";
import { resolveIndexStatus } from "./indexStatus";

describe("resolveIndexStatus", () => {
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
