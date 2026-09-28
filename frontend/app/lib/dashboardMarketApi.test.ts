import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { UnauthorizedError } from "./coreApi";
import {
  fetchSkillDemands,
  fetchSalaryDistribution,
  fetchTrends,
  fetchSnapshotHistory,
} from "./dashboardMarketApi";

describe("dashboardMarketApi", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  describe("fetchSkillDemands", () => {
    it("기본 파라미터로 GET /api/v1/dashboard/skills 를 호출한다", async () => {
      const mockResponse = {
        skills: [
          {
            skillId: "s-1",
            standardName: "Spring Boot",
            demandIndex: 120.5,
            recentDemandRate: 0.15,
            baselineDemandRate: 0.12,
            sources: [
              { source: "SARAMIN", demandIndex: 120.5, sampleSize: 50 },
            ],
            weight: 0.6,
            status: "OK",
          },
        ],
        coverage: "보유 스킬 1개 중 1개 반영 (가중치 100% 재정규화)",
      };

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: mockResponse }),
      });

      const result = await fetchSkillDemands();
      expect(result).toEqual(mockResponse);
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/dashboard/skills",
        expect.objectContaining({ credentials: "include" }),
      );
    });

    it("baseYearMonth 지정 시 쿼리 파라미터를 추가한다", async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          success: true,
          data: { skills: [], coverage: "" },
        }),
      });

      await fetchSkillDemands("202608");
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/dashboard/skills?baseYearMonth=202608",
        expect.objectContaining({ credentials: "include" }),
      );
    });
  });

  describe("fetchSalaryDistribution", () => {
    it("동료집단 연봉 분포 데이터를 정상 조회한다", async () => {
      const mockData = {
        status: "OK",
        sampleSize: 120,
        baseline: {
          source: "KOSIS",
          median: 65000000,
          wageDefinition: "임금직무정보시스템 500인 이상",
        },
        postingReference: {
          min: 45000000,
          p25: 55000000,
          median: 68000000,
          p75: 80000000,
          max: 120000000,
          sampleSize: 45,
          missingRate: 62.5,
        },
        userPosition: {
          grossSalary: 70000000,
          percentile: 65,
        },
        peerGroup: {
          fallbackLevel: 1,
          appliedConditions: "서울 전체 / 경력 4~7년(MID) / 회사규모 무관",
        },
      };

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: mockData }),
      });

      const result = await fetchSalaryDistribution("202608");
      expect(result).toEqual(mockData);
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/dashboard/salary-distribution?baseYearMonth=202608",
        expect.objectContaining({ credentials: "include" }),
      );
    });
  });

  // 세션 게이트가 켜진 뒤로 세션 없는 호출은 전부 401 이다. 평범한 Error 를 던지면 화면이
  // 로그인으로 보내지 못하고 문자열 오류만 띄운다 — 401 판별은 coreApi 한 곳에 모은다.
  describe("세션 만료", () => {
    it.each([
      ["fetchSkillDemands", () => fetchSkillDemands()],
      ["fetchSalaryDistribution", () => fetchSalaryDistribution()],
      ["fetchTrends", () => fetchTrends()],
      ["fetchSnapshotHistory", () => fetchSnapshotHistory()],
    ])("%s 는 401 에 UnauthorizedError 를 던진다", async (_name, call) => {
      global.fetch = vi.fn().mockResolvedValue({ ok: false, status: 401 });

      await expect(call()).rejects.toBeInstanceOf(UnauthorizedError);
    });
  });
});
