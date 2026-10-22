import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  fetchPipelineStats,
  setAdminKey,
  triggerDeduplication,
} from "./collectorApi";

describe("collectorApi (Collector 파이프라인 관리자 API 클라이언트)", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe("API 요청 및 헤더 처리", () => {
    it("X-Admin-Key가 설정된 경우 요청 헤더에 포함하여 전송한다", async () => {
      setAdminKey("my-admin-key");
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ totalRawIngest: 100 }),
      } as unknown as Response);

      await fetchPipelineStats();

      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/stats",
        expect.objectContaining({
          method: "GET",
          headers: expect.objectContaining({
            "Content-Type": "application/json",
            "X-Admin-Key": "my-admin-key",
          }),
        }),
      );
    });

    it("403 Forbidden 응답 시 관리자 키 오류 메시지와 함께 예외를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        json: async () => ({ message: "인증 실패" }),
      } as unknown as Response);

      await expect(fetchPipelineStats()).rejects.toEqual({
        code: "FORBIDDEN",
        message: "인증 실패",
        status: 403,
      });
    });

    it("409 Conflict 응답 시 작업 중복 충돌 예외를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: async () => ({ message: "파이프라인 실행 중" }),
      } as unknown as Response);

      await expect(triggerDeduplication()).rejects.toEqual({
        code: "CONFLICT",
        message: "파이프라인 실행 중",
        status: 409,
      });
    });

    it("기타 HTTP 오류 응답 시 상태 코드 및 메시지를 포함한 예외를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({ code: "INTERNAL", message: "서버 내부 오류" }),
      } as unknown as Response);

      await expect(fetchPipelineStats()).rejects.toEqual({
        code: "INTERNAL",
        message: "서버 내부 오류",
        status: 500,
      });
    });
  });
});
