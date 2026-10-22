import { describe, expect, it } from "vitest";

import { userSkillSchema } from "./skill";

const today = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
};

describe("userSkillSchema", () => {
  it("1개월과 600개월 경계를 받는다", () => {
    expect(
      userSkillSchema.safeParse({
        monthsOfExperience: 1,
        lastUsedDate: today(),
      }).success,
    ).toBe(true);
    expect(
      userSkillSchema.safeParse({
        monthsOfExperience: 600,
        lastUsedDate: today(),
      }).success,
    ).toBe(true);
  });

  it("0개월과 601개월을 거부한다", () => {
    expect(
      userSkillSchema.safeParse({
        monthsOfExperience: 0,
        lastUsedDate: today(),
      }).success,
    ).toBe(false);
    expect(
      userSkillSchema.safeParse({
        monthsOfExperience: 601,
        lastUsedDate: today(),
      }).success,
    ).toBe(false);
  });

  it("오늘 날짜는 받고 내일은 거부한다", () => {
    // toISOString() 은 UTC 로 밀린다 — KST 는 UTC+9 라 자정~09시 사이엔
    // "내일"의 UTC 날짜가 "오늘"의 로컬 날짜와 같아져 이 테스트가 거짓으로
    // 통과(실은 실패)한다. skill.ts 의 today() 와 같은 방식(로컬 달력
    // 필드 직접 조합)으로 맞춘다.
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const iso = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, "0")}-${String(tomorrow.getDate()).padStart(2, "0")}`;

    expect(
      userSkillSchema.safeParse({
        monthsOfExperience: 12,
        lastUsedDate: today(),
      }).success,
    ).toBe(true);
    expect(
      userSkillSchema.safeParse({ monthsOfExperience: 12, lastUsedDate: iso })
        .success,
    ).toBe(false);
  });
});
