import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

  it("빈 질의는 네트워크를 타지 않는다", async () => {
    const spy = vi.fn();
    vi.stubGlobal("fetch", spy);
    await expect(searchSkills("   ")).resolves.toEqual([]);
    expect(spy).not.toHaveBeenCalled();
  });
});
