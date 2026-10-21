import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  createBonus,
  createIncome,
  deleteBonus,
  deleteIncome,
  listBonuses,
  listIncome,
  updateBonusTerms,
  updateIncome,
} from "./income";

describe("income API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("소득 이력을 조회한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: [{ id: "i-1" }] }),
    } as unknown as Response);

    await expect(listIncome()).resolves.toEqual([{ id: "i-1" }]);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("소득 이력을 생성한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { id: "i-2" } }),
    } as unknown as Response);

    const input = {
      grossSalary: 50_000_000,
      netSalary: 3_500_000,
      effectiveDate: "2024-01-01",
    };
    await createIncome(input);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income",
      expect.objectContaining({ method: "POST", body: JSON.stringify(input) }),
    );
  });

  it("소득 이력을 수정한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { id: "i-1" } }),
    } as unknown as Response);

    const input = {
      grossSalary: 55_000_000,
      netSalary: 3_800_000,
      effectiveDate: "2024-06-01",
    };
    await updateIncome("i-1", input);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income/i-1",
      expect.objectContaining({ method: "PUT", body: JSON.stringify(input) }),
    );
  });

  it("소득 이력을 삭제한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => ({}),
    } as unknown as Response);

    await expect(deleteIncome("i-1")).resolves.toBeNull();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income/i-1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("상여 조건을 PUT 으로 보낸다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: [{ id: "i-1" }] }),
    } as unknown as Response);

    const input = {
      applyFromIncomeId: "i-1",
      bonusIncluded: false,
      fixedBonusAnnual: 12_000_000,
    };
    await expect(updateBonusTerms(input)).resolves.toEqual([{ id: "i-1" }]);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income/bonus-terms",
      expect.objectContaining({ method: "PUT", body: JSON.stringify(input) }),
    );
  });

  it("성과급 기록을 조회하고 생성한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        data: [{ id: "b-1", paidMonth: "202603", amount: 5_000_000 }],
      }),
    } as unknown as Response);

    await expect(listBonuses()).resolves.toEqual([
      { id: "b-1", paidMonth: "202603", amount: 5_000_000 },
    ]);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income/bonuses",
      expect.objectContaining({ method: "GET" }),
    );

    await createBonus({ paidMonth: "202603", amount: 5_000_000 });
    expect(globalThis.fetch).toHaveBeenLastCalledWith(
      "/api/v1/income/bonuses",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ paidMonth: "202603", amount: 5_000_000 }),
      }),
    );
  });

  it("성과급 삭제는 본문 없는 204 를 성공으로 읽는다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error("본문 없음");
      },
    } as unknown as Response);

    await expect(deleteBonus("b-1")).resolves.toBeNull();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income/bonuses/b-1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });
});
