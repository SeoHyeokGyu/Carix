import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { UnauthorizedError } from "./coreApi";
import {
  createUserSkill,
  deleteUserSkill,
  fetchSkillRecommendations,
  fetchUserSkills,
  searchSkills,
  updateUserSkill,
} from "./skillApi";

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

  afterEach(() => {
    // `vi.restoreAllMocks()` 는 `vi.fn()` 의 구현·호출 기록만 되돌린다 —
    // `vi.stubGlobal("fetch", ...)` 로 전역에 심은 값은 따로 걷어내야 다음
    // 테스트가 이전 스텁을 물려받지 않는다.
    vi.unstubAllGlobals();
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

/**
 * `coreGet`/`coreSend` 는 경로 앞에 `/api` 를 스스로 붙인다. 호출부가 실수로
 * `/api` 를 다시 붙이면 `/api/api/v1/...` 가 되는데, 응답 처리 로직만 보는
 * 위 "오류 처리" 스위트는 이 클래스의 실수를 잡지 못한다 — 여기서 각 함수가
 * 실제로 어떤 URL·메서드·바디로 fetch 를 호출하는지 직접 확인한다.
 */
describe("skillApi 요청 형태", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("searchSkills 는 GET /api/v1/skills/search 에 인코딩된 질의를 실어 보낸다", async () => {
    const mockFetch = vi
      .fn()
      .mockResolvedValue(mockResponse(200, { success: true, data: [] }));
    vi.stubGlobal("fetch", mockFetch);

    await searchSkills("Spring Boot");

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/v1/skills/search?q=Spring%20Boot",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("fetchUserSkills 는 GET /api/v1/user-skills 를 호출한다", async () => {
    const mockFetch = vi
      .fn()
      .mockResolvedValue(mockResponse(200, { success: true, data: [] }));
    vi.stubGlobal("fetch", mockFetch);

    await fetchUserSkills();

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/v1/user-skills",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("createUserSkill 은 POST /api/v1/user-skills 에 입력을 직렬화해 보낸다", async () => {
    const input = {
      standardName: "Docker",
      monthsOfExperience: 12,
      lastUsedDate: "2026-07-01",
    };
    const mockFetch = vi.fn().mockResolvedValue(
      mockResponse(200, {
        success: true,
        data: { id: "u-2", skillId: "s-2", isCustom: false, ...input },
      }),
    );
    vi.stubGlobal("fetch", mockFetch);

    await createUserSkill(input);

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/v1/user-skills",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(input),
      }),
    );
  });

  it("updateUserSkill 은 PUT /api/v1/user-skills/:id 에 입력을 직렬화해 보낸다", async () => {
    const input = { monthsOfExperience: 40, lastUsedDate: "2026-08-15" };
    const mockFetch = vi.fn().mockResolvedValue(
      mockResponse(200, {
        success: true,
        data: {
          id: "u-1",
          skillId: "s-1",
          standardName: "Java",
          isCustom: false,
          ...input,
        },
      }),
    );
    vi.stubGlobal("fetch", mockFetch);

    await updateUserSkill("u-1", input);

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/v1/user-skills/u-1",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify(input),
      }),
    );
  });

  it("deleteUserSkill 은 DELETE /api/v1/user-skills/:id 를 호출한다", async () => {
    // 실제 백엔드(SkillController#deleteUserSkill)는 204 가 아니라
    // `ResponseEntity.ok(ApiResult.success(null, ...))` — 200 에 봉투를 씌워 돌려준다.
    const mockFetch = vi
      .fn()
      .mockResolvedValue(mockResponse(200, { success: true, data: null }));
    vi.stubGlobal("fetch", mockFetch);

    await deleteUserSkill("u-1");

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/v1/user-skills/u-1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("fetchSkillRecommendations 는 GET /api/v1/skills/recommendations 를 호출하고 data 를 반환한다", async () => {
    const mockData = {
      peerGroup: { appliedConditions: "서울 전체 / 경력 MID / 회사규모 무관" },
      recommendations: [
        {
          standardName: "Kubernetes",
          category: "DEVOPS",
          demandRate: 0.245,
          growthTrend: "UP",
          coOccurrence: { withSkill: "Spring Boot", rate: 0.62 },
          reason: "보유하신 Spring Boot 공고의 62%가 함께 요구합니다.",
        },
      ],
    };
    const mockFetch = vi
      .fn()
      .mockResolvedValue(mockResponse(200, { success: true, data: mockData }));
    vi.stubGlobal("fetch", mockFetch);

    const result = await fetchSkillRecommendations();

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/v1/skills/recommendations",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result).toEqual(mockData);
  });
});
