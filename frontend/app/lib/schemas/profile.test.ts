import { describe, expect, it } from "vitest";
import { JOB_FAMILIES, profileSchema } from "./profile";

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

  it("총 경력은 0~50년이다", () => {
    expect(
      profileSchema.safeParse({ ...valid, yearsOfExperience: -1 }).success,
    ).toBe(false);
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
