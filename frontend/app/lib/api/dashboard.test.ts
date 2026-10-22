import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { UnauthorizedError } from "../coreApi";
import { fetchSummary } from "./dashboard";

describe("dashboard API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
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
});
