import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listUserSkills } from "./userSkills";

describe("userSkills API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("보유 스킬 목록을 조회한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: [{ id: "us-1" }] }),
    } as unknown as Response);

    await expect(listUserSkills()).resolves.toEqual([{ id: "us-1" }]);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/user-skills",
      expect.objectContaining({ method: "GET" }),
    );
  });
});
