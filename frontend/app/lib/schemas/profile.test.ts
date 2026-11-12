import { describe, expect, it } from "vitest";

import { completeProfileSchema, profileSchema, REGIONS } from "./profile";

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

  // 온보딩 「다음」 은 입사일까지 받는다. 퇴직금·근속 계산이 입사일에 기대고, 프리랜서만 입사일이 없을 수 있다.
  describe("completeProfileSchema 입사일", () => {
    it("정규직은 입사일이 없으면 hireDate 오류다", () => {
      const result = completeProfileSchema.safeParse(valid);
      expect(result.success).toBe(false);
      expect(result.error?.issues.map((i) => i.path)).toEqual([["hireDate"]]);
    });

    it("프리랜서는 입사일 없이 통과한다", () => {
      expect(
        completeProfileSchema.safeParse({
          ...valid,
          employmentType: "FREELANCE",
        }).success,
      ).toBe(true);
    });

    it("입사일이 있으면 통과하고, 오늘 이후면 거부한다", () => {
      expect(
        completeProfileSchema.safeParse({ ...valid, hireDate: "2022-03-02" })
          .success,
      ).toBe(true);
      expect(
        completeProfileSchema.safeParse({ ...valid, hireDate: "2999-01-01" })
          .success,
      ).toBe(false);
    });
  });
});
