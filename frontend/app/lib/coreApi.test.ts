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

    await expect(coreGet("/v1/scenarios/draft")).resolves.toBeNull();
  });

  it("401 이면 UnauthorizedError 를 던진다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({
        code: "UNAUTHORIZED",
        message: "인증이 필요합니다.",
      }),
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

  it("ErrorResponse 의 details 를 ApiError 에 싣는다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        code: "INVALID_INPUT",
        message: "입력값 형식이 올바르지 않습니다.",
        details: {
          paidMonth: "아직 오지 않은 달에는 성과급을 기록할 수 없습니다.",
        },
      }),
    } as unknown as Response);

    await expect(coreSend("POST", "/v1/income/bonuses", {})).rejects.toEqual({
      code: "INVALID_INPUT",
      message: "입력값 형식이 올바르지 않습니다.",
      status: 400,
      details: {
        paidMonth: "아직 오지 않은 달에는 성과급을 기록할 수 없습니다.",
      },
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

  it("본문이 없는 요청은 body 키를 포함하지 않는다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error("본문 없음");
      },
    } as unknown as Response);

    await coreSend("DELETE", "/v1/income/abc");

    expect(globalThis.fetch).toHaveBeenCalledWith("/api/v1/income/abc", {
      method: "DELETE",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    });
  });
});
