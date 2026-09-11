import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listExpenses, putExpenses } from "./expense";

describe("expense API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("소비 이력을 조회한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        data: [{ baseYearMonth: "202608" }],
      }),
    } as unknown as Response);

    await expect(listExpenses()).resolves.toEqual([
      { baseYearMonth: "202608" },
    ]);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/expenses",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("당월 소비를 카테고리 목록으로 저장한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        data: {
          baseYearMonth: "202609",
          items: [
            { category: "HOUSING", amount: 750_000, status: "USER_DEFINED" },
          ],
          weightStatus: "OK",
        },
      }),
    } as unknown as Response);

    const items = [{ category: "HOUSING", amount: 750_000 }];
    await putExpenses("202609", items);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/expenses/202609",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ items }),
      }),
    );
  });
});
