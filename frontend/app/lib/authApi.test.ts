import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { fetchSession, logout, logoutAll, register } from "./authApi";

describe("authApi", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe("register (회원가입)", () => {
    it("실패 응답(오류 코드 및 메시지 포함) 시 ApiError를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({
          code: "EMAIL_DUPLICATE",
          message: "이미 사용 중인 이메일입니다.",
        }),
      } as unknown as Response);

      await expect(register("test@carix.kr", "password123")).rejects.toEqual({
        code: "EMAIL_DUPLICATE",
        message: "이미 사용 중인 이메일입니다.",
      });
    });

    it("502 등 JSON 파싱 실패 시 기본 안내 메시지로 ApiError를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        json: async () => {
          throw new Error("Invalid JSON");
        },
      } as unknown as Response);

      await expect(register("test@carix.kr", "password123")).rejects.toEqual({
        code: "UNKNOWN",
        message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      });
    });
  });

  describe("logout (로그아웃)", () => {
    it("credentials: include 옵션으로 로그아웃 POST 요청을 전송한다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
      } as unknown as Response);

      await logout();

      expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/auth/logout", {
        method: "POST",
        credentials: "include",
      });
    });

    // 서버가 세션을 못 지웠는데 화면만 비로그인으로 바뀌면 공용 PC 에 세션이 남는다.
    it.each([
      ["logout", logout],
      ["logoutAll", logoutAll],
    ])("%s — 서버 실패 응답이면 ApiError 를 던진다", async (_, fn) => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ code: "INTERNAL", message: "서버 오류" }),
      } as unknown as Response);

      await expect(fn()).rejects.toEqual({
        code: "INTERNAL",
        message: "서버 오류",
      });
    });

    it.each([
      ["logout", logout],
      ["logoutAll", logoutAll],
    ])("%s — 401 은 이미 세션이 없다는 뜻이라 성공으로 본다", async (_, fn) => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
      } as unknown as Response);

      await expect(fn()).resolves.toBeUndefined();
    });
  });

  describe("fetchSession (세션 조회)", () => {
    it("401 미인증 응답 시 null을 반환한다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
      } as unknown as Response);

      const result = await fetchSession();
      expect(result).toBeNull();
    });

    it("401 외의 오류 상태 코드(500 등)인 경우 ApiError를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({ code: "INTERNAL_ERROR", message: "서버 오류" }),
      } as unknown as Response);

      await expect(fetchSession()).rejects.toEqual({
        code: "INTERNAL_ERROR",
        message: "서버 오류",
      });
    });
  });
});
