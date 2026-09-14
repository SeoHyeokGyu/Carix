import { describe, expect, it } from "vitest";
import { resolveDecisionSignal } from "./decisionSignal";

describe("resolveDecisionSignal", () => {
  it("아는 코드는 프런트 테이블의 문구와 톤을 돌려준다", () => {
    const view = resolveDecisionSignal("UPSKILL");

    expect(view.label).toBe("스킬 보강 우선");
    expect(view.tone).toBe("info");
  });

  it("아는 코드는 서버 문구가 달라도 프런트 테이블을 따른다", () => {
    const view = resolveDecisionSignal("UPSKILL", "스킬 보강");

    expect(view.label).toBe("스킬 보강 우선");
  });

  it("모르는 코드는 서버 문구로 떨어진다", () => {
    const view = resolveDecisionSignal("RELOCATE", "지역 이동 검토");

    expect(view.label).toBe("지역 이동 검토");
    expect(view.tone).toBe("muted");
  });

  it("모르는 코드에 서버 문구도 없으면 코드를 그대로 보여준다 — 빈 문구로 두지 않는다", () => {
    const view = resolveDecisionSignal("RELOCATE");

    expect(view.label).toBe("RELOCATE");
    expect(view.tone).toBe("muted");
  });

  it("코드가 비어 있으면 판정 대기로 본다", () => {
    const view = resolveDecisionSignal("");

    expect(view.label).toBe("판정 대기");
    expect(view.tone).toBe("muted");
  });
});
