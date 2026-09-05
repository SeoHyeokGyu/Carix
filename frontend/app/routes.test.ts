import { describe, expect, it } from "vitest";
import routes from "./routes";

describe("routes configuration", () => {
  it("defines the expected top-level routes and layouts", () => {
    expect(Array.isArray(routes)).toBe(true);
    expect(routes.length).toBeGreaterThan(0);

    // Should include home, login, register, and 3 layouts (onboarding, dashboard, mypage)
    expect(routes).toHaveLength(6);
  });
});
