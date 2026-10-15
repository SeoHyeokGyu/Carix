import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { getLatestScenario, simulate } from "./scenario";

describe("scenario API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  // 이 엔드포인트가 실제로 204 를 받는 유일한 곳이다 — coreGet 은 204 에서 null 을
  // 돌려주지만 호출자의 타입을 넓히지 않는다(coreApi.ts 주석). 여기서 명시적으로
  // `| null` 을 붙였는지 이 테스트로 회귀를 잡는다.
  it("저장된 시나리오가 없으면 204 를 null 로 받는다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => ({}),
    } as unknown as Response);

    await expect(getLatestScenario()).resolves.toBeNull();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/scenarios/latest",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("저장된 시나리오를 조회한다", async () => {
    const body = {
      inputs: {
        stayExpectedRaiseRate: 3.5,
        moveTargetGrossSalary: 72_000_000,
        moveOneTimeCost: 3_000_000,
        expectedUnemploymentMonths: 2,
        isEligibleForUnemploymentBenefit: true,
      },
      result: { id: "s-1" },
      calculatedAt: "2026-09-07T00:00:00Z",
    };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: body }),
    } as unknown as Response);

    await expect(getLatestScenario()).resolves.toEqual(body);
  });

  it("시나리오를 계산한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { id: "s-1" } }),
    } as unknown as Response);

    const input = {
      stayExpectedRaiseRate: 3.5,
      moveTargetGrossSalary: 72_000_000,
      moveOneTimeCost: 3_000_000,
      expectedUnemploymentMonths: 2,
      isEligibleForUnemploymentBenefit: true,
    };
    await simulate(input);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/scenarios/simulate",
      expect.objectContaining({ method: "POST", body: JSON.stringify(input) }),
    );
  });
});
