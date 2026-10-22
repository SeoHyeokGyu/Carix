import { describe, expect, it } from "vitest";

import { bracketLabel, formatAppliedConditions } from "./sourceLabel";

describe("bracketLabel", () => {
  it("경력 구간 코드를 표시명과 KOSIS 연령대로 바꾼다", () => {
    expect(bracketLabel("ENTRY")).toEqual({ label: "신입", age: "20~24세" });
    expect(bracketLabel("JUNIOR")).toEqual({ label: "주니어", age: "25~29세" });
    expect(bracketLabel("MID")).toEqual({ label: "미들", age: "30~34세" });
    expect(bracketLabel("SENIOR")).toEqual({ label: "시니어", age: "35~39세" });
    expect(bracketLabel("LEAD")).toEqual({ label: "리드", age: "40~44세" });
  });
});

describe("formatAppliedConditions", () => {
  it("비교 조건 문자열 안의 경력 구간·회사 규모·고용 형태 영문 코드를 한국어로 바꾼다", () => {
    expect(
      formatAppliedConditions("서울 전체 / 경력 MID / 회사규모 무관"),
    ).toBe("서울 전체 / 경력 미들 / 회사규모 무관");
    expect(formatAppliedConditions("서울 / 경력 MID±1 / 회사규모 무관")).toBe(
      "서울 / 경력 미들±1 / 회사규모 무관",
    );
    expect(
      formatAppliedConditions("서울 전체 / 경력 4~7년(MID) / 회사규모 무관"),
    ).toBe("서울 전체 / 경력 4~7년(미들) / 회사규모 무관");
    expect(
      formatAppliedConditions("서울 전체 / MID (4~7년) / LARGE / FULL_TIME"),
    ).toBe("서울 전체 / 미들 (4~7년) / 대기업 / 정규직");
    expect(
      formatAppliedConditions(
        "서울 전체 / JUNIOR·MID (0~7년) / MEDIUM / CONTRACT",
      ),
    ).toBe("서울 전체 / 주니어·미들 (0~7년) / 중견·중소 / 계약직");
  });
});
