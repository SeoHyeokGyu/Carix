import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { getProfile, patchProfile } from "./profile";

describe("profile API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("프로필을 조회한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { jobFamily: "DEVELOPER" } }),
    } as unknown as Response);

    await expect(getProfile()).resolves.toEqual({ jobFamily: "DEVELOPER" });
  });

  it("부분 필드만 보내 저장한다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: { jobFamily: "DATA" } }),
    } as unknown as Response);

    await patchProfile({ jobFamily: "DATA" });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/profile",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ jobFamily: "DATA" }),
      }),
    );
  });
});
