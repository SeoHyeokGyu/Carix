import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import {
  searchSkills,
  fetchUserSkills,
  createUserSkill,
  updateUserSkill,
  deleteUserSkill,
} from "./skillApi";

describe("skillApi", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  describe("searchSkills", () => {
    it("빈 검색어는 API를 호출하지 않고 빈 배열을 반환한다", async () => {
      const mockFetch = vi.fn();
      global.fetch = mockFetch;

      const result = await searchSkills("   ");
      expect(result).toEqual([]);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("검색어 입력 시 GET /api/v1/skills/search 호출 후 결과를 반환한다", async () => {
      const mockData = [
        { id: "1", standardName: "Spring Boot", isCustom: false },
        { id: "2", standardName: "Spring Framework", isCustom: false },
      ];
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: mockData }),
      });

      const result = await searchSkills("Spring");
      expect(result).toEqual(mockData);
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/skills/search?q=Spring",
        expect.objectContaining({ credentials: "include" }),
      );
    });
  });

  describe("fetchUserSkills", () => {
    it("사용자의 보유 스킬 목록을 성공적으로 조회한다", async () => {
      const mockSkills = [
        {
          id: "u-1",
          skillId: "s-1",
          standardName: "Java",
          isCustom: false,
          monthsOfExperience: 36,
          lastUsedDate: "2026-08-01",
        },
      ];
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: mockSkills }),
      });

      const result = await fetchUserSkills();
      expect(result).toEqual(mockSkills);
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/user-skills",
        expect.objectContaining({ credentials: "include" }),
      );
    });
  });

  describe("createUserSkill", () => {
    it("새로운 보유 스킬을 등록한다", async () => {
      const input = {
        standardName: "Docker",
        monthsOfExperience: 12,
        lastUsedDate: "2026-07-01",
      };
      const created = {
        id: "u-2",
        skillId: "s-2",
        standardName: "Docker",
        isCustom: false,
        monthsOfExperience: 12,
        lastUsedDate: "2026-07-01",
      };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: created }),
      });

      const result = await createUserSkill(input);
      expect(result).toEqual(created);
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/user-skills",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify(input),
        }),
      );
    });

    it("서버 에러 시 에러 메시지를 던진다", async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => ({ message: "이미 등록된 스킬입니다." }),
      });

      await expect(
        createUserSkill({
          standardName: "Java",
          monthsOfExperience: 10,
          lastUsedDate: "2026-01-01",
        }),
      ).rejects.toThrow("이미 등록된 스킬입니다.");
    });
  });

  describe("updateUserSkill", () => {
    it("보유 스킬 정보를 수정한다", async () => {
      const input = {
        monthsOfExperience: 40,
        lastUsedDate: "2026-08-15",
      };
      const updated = {
        id: "u-1",
        skillId: "s-1",
        standardName: "Java",
        isCustom: false,
        monthsOfExperience: 40,
        lastUsedDate: "2026-08-15",
      };
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: updated }),
      });

      const result = await updateUserSkill("u-1", input);
      expect(result).toEqual(updated);
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/user-skills/u-1",
        expect.objectContaining({
          method: "PUT",
          body: JSON.stringify(input),
        }),
      );
    });
  });

  describe("deleteUserSkill", () => {
    it("보유 스킬을 성공적으로 삭제한다", async () => {
      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: true, data: null }),
      });

      await deleteUserSkill("u-1");
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/v1/user-skills/u-1",
        expect.objectContaining({ method: "DELETE" }),
      );
    });
  });
});
