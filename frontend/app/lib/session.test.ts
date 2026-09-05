import { describe, expect, it } from "vitest";
import { ANONYMOUS, LOADING } from "./session";

describe("session constants", () => {
  it("defines LOADING session", () => {
    expect(LOADING).toEqual({ status: "loading" });
  });

  it("defines ANONYMOUS session", () => {
    expect(ANONYMOUS).toEqual({ status: "anonymous" });
  });
});
