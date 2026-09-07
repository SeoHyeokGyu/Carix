import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { coreGet, coreSend, UnauthorizedError } from "./coreApi";

describe("coreApi", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("ApiResult 봉투를 벗겨 data 만 돌려준다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        data: { jobFamily: "DEVELOPER" },
        message: "요청이 성공적으로 처리되었습니다.",
      }),
    } as unknown as Response);

    await expect(coreGet("/v1/profile")).resolves.toEqual({
      jobFamily: "DEVELOPER",
    });
    expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/profile", {
      method: "GET",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    });
  });

  it("204 응답은 null 로 돌려준다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error("본문 없음");
      },
    } as unknown as Response);

    await expect(coreGet("/v1/scenarios/latest")).resolves.toBeNull();
  });

  it("401 이면 UnauthorizedError 를 던진다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ code: "UNAUTHORIZED", message: "인증이 필요합니다." }),
    } as unknown as Response);

    await expect(coreGet("/v1/profile")).rejects.toBeInstanceOf(
      UnauthorizedError,
    );
  });

  it("ErrorResponse 의 code 와 message 를 ApiError 로 옮긴다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        code: "DUPLICATE_EFFECTIVE_DATE",
        message: "같은 유효 시작일이 이미 있습니다.",
      }),
    } as unknown as Response);

    await expect(
      coreSend("POST", "/v1/income", { grossSalary: 1 }),
    ).rejects.toEqual({
      code: "DUPLICATE_EFFECTIVE_DATE",
      message: "같은 유효 시작일이 이미 있습니다.",
      status: 409,
    });
  });

  it("JSON 이 아닌 오류 응답에는 기본 문구를 쓴다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      json: async () => {
        throw new Error("not json");
      },
    } as unknown as Response);

    await expect(coreGet("/v1/profile")).rejects.toMatchObject({
      code: "UNKNOWN",
      status: 502,
    });
  });

  it("본문이 있는 요청은 JSON 으로 직렬화해 보낸다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { id: "1" } }),
    } as unknown as Response);

    await coreSend("PUT", "/v1/expenses/202609", { items: [] });

    expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/expenses/202609", {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items: [] }),
    });
  });
});
