import { describe, expect, it } from "vitest";
import { sourceLabel } from "./sourceLabel";

describe("sourceLabel", () => {
  it("원천 코드를 표시명으로 바꾼다", () => {
    expect(sourceLabel("JOBKOREA")).toBe("잡코리아");
    expect(sourceLabel("SARAMIN")).toBe("사람인");
    expect(sourceLabel("WANTED")).toBe("원티드");
  });
  it("모르는 코드는 그대로 둔다", () => {
    expect(sourceLabel("KOSIS")).toBe("KOSIS");
  });
});
