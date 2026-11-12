import { describe, expect, it } from "vitest";

import {
  compareHint,
  cumulativeGap,
  equalizingLine,
  firstYearItems,
  splitAssumptions,
} from "./simulatorCompare";

describe("compareHint", () => {
  it("비교 결과가 있으면 안내가 없다", () => {
    expect(compareHint(true, "benefit")).toBeNull();
  });
  it("결과가 없고 실업급여만 빠졌으면 실업급여 안내", () => {
    expect(compareHint(false, "benefit")).toBe(
      "실업급여를 받을 수 있는지 고르면 비교가 나옵니다",
    );
  });
  it("결과가 없고 연봉이 비었으면 연봉 안내", () => {
    const hint = "새 회사 연봉과 실업급여 여부를 넣으면 비교가 나옵니다";
    expect(compareHint(false, "gross")).toBe(hint);
    expect(compareHint(false, null)).toBe(hint);
  });
  // 연봉·실업급여를 이미 채운 사람에게 그 둘을 넣으라고 하면 무엇을 고칠지 모른다.
  it("결과가 없고 다른 칸이 무효면 입력을 고치라는 안내", () => {
    expect(compareHint(false, "other")).toBe("입력을 고치면 비교가 나옵니다");
  });
});

describe("equalizingLine", () => {
  it("계산 못 하면 값만 계산할 수 없음", () => {
    expect(equalizingLine(null, 3.5)).toEqual({
      value: "계산할 수 없음",
      note: null,
    });
  });
  it("예상 인상률이 이미 같거나 넘으면 그렇게 말한다", () => {
    expect(equalizingLine(3.5, 3.5).note).toBe(
      "올해 예상 3.5%로 이미 넘습니다",
    );
  });
  it("예상보다 높으면 대비 문장", () => {
    expect(equalizingLine(15.7, 3.5)).toEqual({
      value: "15.7%",
      note: "지금 회사에서 이만큼 오르면 4년 누적이 같아집니다 · 올해 예상 3.5%",
    });
  });
});

describe("cumulativeGap", () => {
  it("짧은 쪽 길이까지 새 회사 − 지금 회사", () => {
    expect(cumulativeGap([10, 20, 30], [5, 25])).toEqual([-5, 5]);
  });
});

describe("firstYearItems", () => {
  const base = {
    salaryDiff: 100,
    gap: -40,
    oneTimeCost: -10,
    benefit: 0,
    severance: 0,
    total: 50,
  };
  it("실업급여 0 은 빼고 합계를 마지막에", () => {
    expect(firstYearItems(base).map((i) => i.label)).toEqual([
      "연봉 차이",
      "쉬는 기간 소득",
      "일회성 비용",
      "합계",
    ]);
  });
  it("실업급여가 있으면 넣는다", () => {
    expect(
      firstYearItems({ ...base, benefit: 20, total: 70 }).map((i) => i.key),
    ).toContain("benefit");
  });
  // 항이 없으면 퇴직금이 합계에만 들어가 막대 합이 합계와 어긋난다.
  it("퇴직금이 있으면 합계 바로 앞에, 0 이면 뺀다", () => {
    const labels = firstYearItems({
      ...base,
      severance: 3_000_000,
      total: 3_000_050,
    }).map((i) => i.label);
    expect(labels.slice(-2)).toEqual(["퇴직금", "합계"]);
    expect(firstYearItems(base).map((i) => i.label)).not.toContain("퇴직금");
  });
});

describe("splitAssumptions", () => {
  it("새 회사 퇴직금 가정만 경고로 꺼낸다", () => {
    const { warnings, rest } = splitAssumptions([
      { code: "TAX_SINGLE", text: "a" },
      { code: "NEW_JOB_SEVERANCE", text: "b" },
    ]);
    expect(warnings).toEqual(["b"]);
    expect(rest.map((a) => a.code)).toEqual(["TAX_SINGLE"]);
  });
});
