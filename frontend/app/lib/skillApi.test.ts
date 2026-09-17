import { beforeEach, describe, expect, it, vi } from "vitest";
import { UnauthorizedError } from "./coreApi";
import { deleteUserSkill, fetchUserSkills, searchSkills } from "./skillApi";

function mockResponse(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

describe("skillApi 오류 처리", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("401 이면 UnauthorizedError 를 던진다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(mockResponse(401, { message: "무효" })),
    );
    await expect(fetchUserSkills()).rejects.toBeInstanceOf(UnauthorizedError);
  });

  it("409 는 code·message·status 를 가진 ApiError 로 던진다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockResponse(409, {
          code: "DUPLICATE_SKILL",
          message: "이미 등록된 스킬입니다.",
        }),
      ),
    );
    await expect(deleteUserSkill("skill-1")).rejects.toMatchObject({
      code: "DUPLICATE_SKILL",
      message: "이미 등록된 스킬입니다.",
      status: 409,
    });
  });

  it("검색은 봉투를 벗겨 data 만 돌려준다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockResponse(200, {
          success: true,
          data: [{ id: "s1", standardName: "Spring Boot", isCustom: false }],
        }),
      ),
    );
    await expect(searchSkills("spring")).resolves.toEqual([
      { id: "s1", standardName: "Spring Boot", isCustom: false },
    ]);
  });

  it("빈 질의는 네트워크를 타지 않는다", async () => {
    const spy = vi.fn();
    vi.stubGlobal("fetch", spy);
    await expect(searchSkills("   ")).resolves.toEqual([]);
    expect(spy).not.toHaveBeenCalled();
  });
});
