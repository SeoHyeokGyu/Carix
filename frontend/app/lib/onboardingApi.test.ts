import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { UnauthorizedError } from "./coreApi";
import { advanceOnboarding, resetOnboarding } from "./onboardingApi";

describe("onboardingApi", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  // Critical — coreApi 의 401 처리 지점과 다른 경로로 일반 Error 를 던지면, 이
  // 모듈을 부르는 화면들의 `error instanceof UnauthorizedError` 분기가 걸리지
  // 않아 로그인으로 보내지 못하고 죽은 세션에 남는다.
  it("401 이면 UnauthorizedError 를 던진다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({
        code: "UNAUTHORIZED",
        message: "인증이 필요합니다.",
      }),
    } as unknown as Response);

    await expect(advanceOnboarding("INCOME")).rejects.toBeInstanceOf(
      UnauthorizedError,
    );
  });

  describe("resetOnboarding", () => {
    it("401 이면 UnauthorizedError 를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({
          code: "UNAUTHORIZED",
          message: "인증이 필요합니다.",
        }),
      } as unknown as Response);

      await expect(resetOnboarding()).rejects.toBeInstanceOf(UnauthorizedError);
    });
  });
});
