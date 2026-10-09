import { describe, expect, it } from "vitest";
import { bracketLabel, familyLabel, sourceLabel } from "./sourceLabel";

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

describe("bracketLabel", () => {
  it("경력 구간 코드를 표시명과 KOSIS 연령대로 바꾼다", () => {
    expect(bracketLabel("ENTRY")).toEqual({ label: "신입", age: "20~24세" });
    expect(bracketLabel("JUNIOR")).toEqual({ label: "주니어", age: "25~29세" });
    expect(bracketLabel("MID")).toEqual({ label: "미드", age: "30~34세" });
    expect(bracketLabel("SENIOR")).toEqual({ label: "시니어", age: "35~39세" });
    expect(bracketLabel("LEAD")).toEqual({ label: "리드", age: "40~44세" });
  });
  it("모르는 코드는 그대로 두고 연령대는 비운다", () => {
    expect(bracketLabel("ALL")).toEqual({ label: "ALL", age: null });
  });
});
