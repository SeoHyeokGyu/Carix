import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchSession, login, logout, register } from "./authApi";

describe("authApi", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe("register (회원가입)", () => {
    it("정상적으로 가입하고 세션 정보를 반환한다", async () => {
      const mockSession = {
        userId: "user-123",
        email: "test@carix.kr",
        onboardingStep: "PROFILE" as const,
      };

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockSession,
      } as unknown as Response);

      const result = await register("test@carix.kr", "password123");

      expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/auth/register", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: "test@carix.kr",
          password: "password123",
        }),
      });
      expect(result).toEqual(mockSession);
    });

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

    it("응답에 코드가 없는 경우 UNKNOWN 코드로 ApiError를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({ message: "알 수 없는 에러" }),
      } as unknown as Response);

      await expect(register("test@carix.kr", "password123")).rejects.toEqual({
        code: "UNKNOWN",
        message: "알 수 없는 에러",
      });
    });

    it("응답 본문이 비어있거나 메시지가 없는 경우 기본 ApiError를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({}),
      } as unknown as Response);

      await expect(register("test@carix.kr", "password123")).rejects.toEqual({
        code: "UNKNOWN",
        message: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
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

  describe("login (로그인)", () => {
    it("정상적으로 로그인하고 세션 정보를 반환한다", async () => {
      const mockSession = {
        userId: "user-456",
        email: "login@carix.kr",
        onboardingStep: "COMPLETED" as const,
      };

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockSession,
      } as unknown as Response);

      const result = await login("login@carix.kr", "securepassword");

      expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/auth/login", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: "login@carix.kr",
          password: "securepassword",
        }),
      });
      expect(result).toEqual(mockSession);
    });

    it("로그인 실패 시 ApiError를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({
          code: "INVALID_CREDENTIALS",
          message: "이메일 또는 비밀번호가 일치하지 않습니다.",
        }),
      } as unknown as Response);

      await expect(login("login@carix.kr", "wrongpass")).rejects.toEqual({
        code: "INVALID_CREDENTIALS",
        message: "이메일 또는 비밀번호가 일치하지 않습니다.",
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
  });

  describe("fetchSession (세션 조회)", () => {
    it("200 정상 응답 시 SessionResponse를 반환한다", async () => {
      const mockSession = {
        userId: "user-789",
        email: "session@carix.kr",
        onboardingStep: "INCOME" as const,
      };

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => mockSession,
      } as unknown as Response);

      const result = await fetchSession();

      expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/auth/session", {
        credentials: "include",
      });
      expect(result).toEqual(mockSession);
    });

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
