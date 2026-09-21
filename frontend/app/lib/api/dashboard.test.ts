import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { UnauthorizedError } from "../coreApi";
import { fetchSummary } from "./dashboard";

describe("dashboard API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("지수 요약을 조회한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { purchasingPowerIndex: {} } }),
    } as unknown as Response);

    await expect(fetchSummary()).resolves.toEqual({
      ok: true,
      data: { purchasingPowerIndex: {} },
    });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/dashboard/summary",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("401 이면 UnauthorizedError 를 그대로 던진다(감싸지 않는다)", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({}),
    } as unknown as Response);

    await expect(fetchSummary()).rejects.toBeInstanceOf(UnauthorizedError);
  });

  it("401 이 아닌 오류는 던지지 않고 감싸서 반환한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      json: async () => ({
        code: "UNKNOWN",
        message: "요청을 처리하지 못했습니다.",
      }),
    } as unknown as Response);

    await expect(fetchSummary()).resolves.toEqual({
      ok: false,
      error: {
        code: "UNKNOWN",
        message: "요청을 처리하지 못했습니다.",
        status: 502,
      },
    });
  });

  describe("recalculateDashboard", () => {
    it("재계산을 요청하고 결과를 반환한다", async () => {
      const mockResult = {
        recalculatedMonths: 5,
        fromYearMonth: "202601",
        summary: { purchasingPowerIndex: {} },
      };
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ success: true, data: mockResult }),
      } as unknown as Response);

      const { recalculateDashboard } = await import("./dashboard");
      await expect(recalculateDashboard("202601")).resolves.toEqual({
        ok: true,
        data: mockResult,
      });
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/dashboard/recalculate?fromYearMonth=202601",
        expect.objectContaining({ method: "POST" }),
      );
    });

    it("401 이면 UnauthorizedError 를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({}),
      } as unknown as Response);

      const { recalculateDashboard } = await import("./dashboard");
      await expect(recalculateDashboard()).rejects.toBeInstanceOf(
        UnauthorizedError,
      );
    });
  });
});
