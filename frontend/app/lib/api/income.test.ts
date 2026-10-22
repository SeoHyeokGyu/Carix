import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { deleteBonus } from "./income";

describe("income API", () => {
  const originalFetch = globalThis.fetch;
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("성과급 삭제는 본문 없는 204 를 성공으로 읽는다", async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error("본문 없음");
      },
    } as unknown as Response);

    await expect(deleteBonus("b-1")).resolves.toBeNull();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/income/bonuses/b-1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });
});
