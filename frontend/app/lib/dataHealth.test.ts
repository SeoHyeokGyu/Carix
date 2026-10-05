import { describe, expect, it } from "vitest";
import { sortChecks, VERDICT_LABEL } from "./dataHealth";
import type { HealthCheck } from "./collectorApi";

const c = (id: string, verdict: HealthCheck["verdict"]): HealthCheck => ({
  id,
  title: id,
  verdict,
  message: "",
  impactUsers: null,
  action: null,
  actionLink: null,
  drillDown: null,
  evidence: {},
});

describe("dataHealth", () => {
  it("문제 → 주의 → 판정 불가 → 정상 순으로 정렬한다", () => {
    expect(
      sortChecks([
        c("a", "OK"),
        c("b", "UNKNOWN"),
        c("c", "ERROR"),
        c("d", "WARN"),
      ]).map((x) => x.id),
    ).toEqual(["c", "d", "b", "a"]);
  });

  it("판정 라벨은 정상·주의·문제·판정 불가다", () => {
    expect(VERDICT_LABEL).toEqual({
      OK: "정상",
      WARN: "주의",
      ERROR: "문제",
      UNKNOWN: "판정 불가",
    });
  });
});
