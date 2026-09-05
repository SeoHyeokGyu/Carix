import { describe, expect, it } from "vitest";
import { ANONYMOUS, LOADING } from "./session";

describe("session 상수", () => {
  it("LOADING 세션 상수를 정의한다", () => {
    expect(LOADING).toEqual({ status: "loading" });
  });

  it("ANONYMOUS 세션 상수를 정의한다", () => {
    expect(ANONYMOUS).toEqual({ status: "anonymous" });
  });
});
