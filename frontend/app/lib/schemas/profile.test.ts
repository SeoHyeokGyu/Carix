import { describe, expect, it } from "vitest";

import { profileSchema, REGIONS } from "./profile";

describe("profileSchema", () => {
  const valid = {
    jobFamily: "DEVELOPER",
    yearsOfExperience: 5,
    region: "서울",
    employmentType: "FULL_TIME",
    companySize: "MEDIUM",
  };

  it("지역은 collector 의 시도 화이트리스트 17개만, 정식 명칭 없이 받는다", () => {
    expect(REGIONS.map((o) => o.value)).toEqual([
      "서울",
      "부산",
      "대구",
      "인천",
      "광주",
      "대전",
      "울산",
      "세종",
      "경기",
      "강원",
      "충북",
      "충남",
      "전북",
      "전남",
      "경북",
      "경남",
      "제주",
    ]);
    // collector 어휘는 축약형("서울")이다. 정식 명칭("서울특별시")은 그대로
    // 통과시키면 market_aggregate 조회가 조용히 비어 "표본 부족"이 된다.
    expect(
      profileSchema.safeParse({ ...valid, region: "서울특별시" }).success,
    ).toBe(false);
  });

  it("총 경력은 0~50년이다", () => {
    expect(
      profileSchema.safeParse({ ...valid, yearsOfExperience: -1 }).success,
    ).toBe(false);
    expect(
      profileSchema.safeParse({ ...valid, yearsOfExperience: 0 }).success,
    ).toBe(true);
    expect(
      profileSchema.safeParse({ ...valid, yearsOfExperience: 50 }).success,
    ).toBe(true);
    expect(
      profileSchema.safeParse({ ...valid, yearsOfExperience: 51 }).success,
    ).toBe(false);
  });
});
