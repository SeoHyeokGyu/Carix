import { describe, expect, expectTypeOf, it } from "vitest";

import {
  COMPANY_SIZES,
  EMPLOYMENT_TYPES,
  JOB_FAMILIES,
  type ProfileInput,
  profileSchema,
  REGIONS,
} from "./profile";

describe("profileSchema", () => {
  const valid = {
    jobFamily: "DEVELOPER",
    yearsOfExperience: 5,
    region: "서울",
    employmentType: "FULL_TIME",
    companySize: "MEDIUM",
  };

  it("정상 입력을 통과시킨다", () => {
    expect(profileSchema.safeParse(valid).success).toBe(true);
  });

  it("직군은 집계 키 네 값만 받는다", () => {
    expect(JOB_FAMILIES.map((o) => o.value)).toEqual([
      "DEVELOPER",
      "DATA",
      "AI",
      "OTHER",
    ]);
    expect(
      profileSchema.safeParse({ ...valid, jobFamily: "BACKEND" }).success,
    ).toBe(false);
  });

  it("jobFamily 타입은 컴파일 타임에 네 리터럴의 유니온으로 좁혀진다", () => {
    // values() 가 Option[] 로 넓어지면 이 타입은 string 이 되어, 오타를
    // 컴파일 타임에 잡지 못하게 된다 — join key 실패가 조용해서 위험하다.
    expectTypeOf<ProfileInput["jobFamily"]>().toEqualTypeOf<
      "DEVELOPER" | "DATA" | "AI" | "OTHER"
    >();
  });

  it("고용형태는 집계 키 네 값만 받는다", () => {
    expect(EMPLOYMENT_TYPES.map((o) => o.value)).toEqual([
      "FULL_TIME",
      "CONTRACT",
      "FREELANCE",
      "INTERN",
    ]);
    expect(
      profileSchema.safeParse({ ...valid, employmentType: "PART_TIME" })
        .success,
    ).toBe(false);
  });

  it("회사 규모는 집계 키 세 값만 받는다", () => {
    expect(COMPANY_SIZES.map((o) => o.value)).toEqual([
      "LARGE",
      "MEDIUM",
      "STARTUP",
    ]);
    expect(
      profileSchema.safeParse({ ...valid, companySize: "SMALL" }).success,
    ).toBe(false);
  });

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

  it("회사 규모는 선택이다", () => {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { companySize: _drop, ...withoutSize } = valid;
    expect(profileSchema.safeParse(withoutSize).success).toBe(true);
  });
});
