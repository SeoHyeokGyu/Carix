import { describe, expect, it } from "vitest";
import routes from "./routes";

describe("라우트 설정 (routes)", () => {
  it("최상위 라우트 및 레이아웃 목록을 올바르게 정의한다", () => {
    expect(Array.isArray(routes)).toBe(true);
    expect(routes.length).toBeGreaterThan(0);

    // Should include home, login, register, and 3 layouts (onboarding, dashboard, mypage)
    expect(routes).toHaveLength(6);
  });
});
