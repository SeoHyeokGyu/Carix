import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAdminKey,
  fetchJobStatus,
  fetchPipelineStats,
  fetchRecentJobs,
  fetchRecentPostings,
  getAdminKey,
  setAdminKey,
  triggerCrawl,
  triggerDeduplication,
  triggerMarketAggregate,
  triggerSeedLoad,
  triggerWageStat,
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

  describe("관리자 시크릿 키 로컬 스토리지 관리", () => {
    it("기본 키 값은 빈 문자열을 반환한다", () => {
      expect(getAdminKey()).toBe("");
    });

    it("키를 저장하고 정상 조회한다", () => {
      setAdminKey(" secret-key-123 ");
      expect(getAdminKey()).toBe("secret-key-123");
    });

    it("키를 삭제하면 빈 문자열이 된다", () => {
      setAdminKey("secret");
      clearAdminKey();
      expect(getAdminKey()).toBe("");
    });
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

    it("403 Forbidden 응답에서 JSON 파싱 실패 시 기본 403 메시지를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        json: async () => {
          throw new Error("Invalid JSON");
        },
      } as unknown as Response);

      await expect(fetchPipelineStats()).rejects.toEqual(
        expect.objectContaining({
          code: "FORBIDDEN",
          status: 403,
        }),
      );
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

    it("409 Conflict 응답에서 JSON 파싱 실패 시 기본 409 안내 메시지를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: async () => {
          throw new Error("Invalid JSON");
        },
      } as unknown as Response);

      await expect(triggerDeduplication()).rejects.toEqual(
        expect.objectContaining({
          code: "CONFLICT",
          status: 409,
        }),
      );
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

    it("기타 HTTP 오류 응답에서 JSON 파싱 실패 시 UNKNOWN_ERROR를 던진다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        json: async () => {
          throw new Error("Bad Gateway");
        },
      } as unknown as Response);

      await expect(fetchPipelineStats()).rejects.toEqual({
        code: "UNKNOWN_ERROR",
        message: "요청에 실패했습니다. (상태 코드: 502)",
        status: 502,
      });
    });
  });

  describe("파이프라인 조회 및 트리거 함수", () => {
    it("fetchRecentJobs() 호출 시 최근 작업 목록을 조회한다", async () => {
      const mockJobs = [
        { jobId: "job-1", jobType: "CRAWL", status: "SUCCESS" },
      ];
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockJobs,
      } as unknown as Response);

      const res = await fetchRecentJobs();
      expect(res).toEqual(mockJobs);
    });

    it("fetchJobStatus(jobId) 호출 시 특정 작업 상태를 조회한다", async () => {
      const mockJob = { jobId: "job-123", status: "RUNNING" };
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockJob,
      } as unknown as Response);

      const res = await fetchJobStatus("job-123");
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/jobs/job-123",
        expect.anything(),
      );
      expect(res).toEqual(mockJob);
    });

    it("fetchRecentPostings(limit) 호출 시 최근 수집 공고를 조회한다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      } as unknown as Response);

      await fetchRecentPostings(10);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/recent-postings?limit=10",
        expect.anything(),
      );

      await fetchRecentPostings();
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/recent-postings?limit=20",
        expect.anything(),
      );
    });

    it("triggerCrawl() 호출 시 크롤링 작업을 실행한다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ jobId: "crawl-1" }),
      } as unknown as Response);

      await triggerCrawl({ portal: "SARAMIN", maxPages: 5 });
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/crawl",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ portal: "SARAMIN", maxPages: 5 }),
        }),
      );

      await triggerCrawl();
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/crawl",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({}),
        }),
      );
    });

    it("triggerWageStat() 호출 시 임금통계 집계를 실행한다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ jobId: "wage-1" }),
      } as unknown as Response);

      await triggerWageStat({ startYear: "2023" });
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/wage-stat",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ startYear: "2023" }),
        }),
      );
    });

    it("triggerMarketAggregate() 호출 시 시장 통계 집계를 실행한다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ jobId: "agg-1" }),
      } as unknown as Response);

      await triggerMarketAggregate({ all: true });
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/aggregate",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ all: true }),
        }),
      );
    });

    it("triggerSeedLoad() 호출 시 시드 데이터 로드를 실행한다", async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ jobId: "seed-1" }),
      } as unknown as Response);

      await triggerSeedLoad({ dataDir: "/seeds" });
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v1/admin/collector/seed-load",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ dataDir: "/seeds" }),
        }),
      );
    });
  });
});
