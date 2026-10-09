import { describe, expect, it } from "vitest";
import { familyLabel, sourceLabel } from "./sourceLabel";

describe("sourceLabel", () => {
  it("원천 코드를 표시명으로 바꾼다", () => {
    expect(sourceLabel("JOBKOREA")).toBe("잡코리아");
    expect(sourceLabel("SARAMIN")).toBe("사람인");
    expect(sourceLabel("WANTED")).toBe("원티드");
  });
  it("점핏을 표시명으로 바꾼다", () => {
    expect(sourceLabel("JUMPIT")).toBe("점핏");
  });
  it("모르는 코드는 그대로 둔다", () => {
    expect(sourceLabel("KOSIS")).toBe("KOSIS");
  });
});

describe("familyLabel", () => {
  it("직무 코드를 표시명으로 바꾼다", () => {
    expect(familyLabel("DEVELOPER")).toBe("개발");
    expect(familyLabel("DATA")).toBe("데이터");
    expect(familyLabel("AI")).toBe("AI");
    expect(familyLabel("OTHER")).toBe("기타");
  });
  it("모르는 코드는 그대로 둔다", () => {
    expect(familyLabel("QA")).toBe("QA");
  });
});
