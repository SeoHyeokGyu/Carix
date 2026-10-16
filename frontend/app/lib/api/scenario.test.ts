import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { listScenarios, simulate } from "./scenario";

describe("scenario API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("저장된 시나리오 목록을 조회한다", async () => {
    const body = [
      {
        id: "s-2",
        scenarioName: null,
        inputs: {
          scenarioName: null,
          stayExpectedRaiseRate: 3.5,
          moveTargetGrossSalary: 72_000_000,
          moveOneTimeCost: 3_000_000,
          expectedUnemploymentMonths: 2,
          isEligibleForUnemploymentBenefit: true,
        },
        result: { id: "s-2" },
        calculatedAt: "2026-09-07T00:00:00Z",
      },
    ];
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: body }),
    } as unknown as Response);

    await expect(listScenarios()).resolves.toEqual(body);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/scenarios",
      expect.objectContaining({ method: "GET" }),
    );
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
