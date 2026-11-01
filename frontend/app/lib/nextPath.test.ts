import { describe, expect, test } from "vitest";

import { safeNextPath, withNext } from "./nextPath";

describe("safeNextPath — 돌아갈 경로", () => {
  test.each([
    ["/dashboard/simulator?resumeSave=1", "/dashboard/simulator?resumeSave=1"],
    ["/mypage/profile", "/mypage/profile"],
  ])("앱 내부 경로 %s 는 그대로 쓴다", (raw, expected) => {
    expect(safeNextPath(raw)).toBe(expected);
  });

  test.each([
    "//evil.example",
    "/\\evil.example",
    "https://evil.example",
    "",
    null,
    undefined,
    "dashboard",
    "/\t/evil.com",
    "/\n/evil.com",
    "/\t\\evil.com",
  ])("외부·잘못된 경로 %s 는 버린다", (raw) => {
    expect(safeNextPath(raw)).toBeNull();
  });
});

describe("withNext", () => {
  test("돌아갈 경로를 인코딩해 쿼리로 붙인다", () => {
    expect(withNext("/register", "/dashboard/simulator?resumeSave=1")).toBe(
      "/register?next=%2Fdashboard%2Fsimulator%3FresumeSave%3D1",
    );
  });
});
