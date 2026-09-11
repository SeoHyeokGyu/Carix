import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { UnauthorizedError } from "./coreApi";
import { advanceOnboarding } from "./onboardingApi";

describe("onboardingApi", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("성공하면 응답 본문을 그대로 돌려준다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ currentStep: "INCOME", completed: false }),
    } as unknown as Response);

    await expect(advanceOnboarding("INCOME")).resolves.toEqual({
      currentStep: "INCOME",
      completed: false,
    });
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

  it("401 이 아닌 실패는 일반 오류를 던진다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ code: "SERVER_ERROR", message: "서버 오류" }),
    } as unknown as Response);

    await expect(advanceOnboarding("INCOME")).rejects.toThrow(
      "온보딩 단계를 갱신하지 못했습니다.",
    );
  });
});
