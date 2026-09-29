import { describe, expect, it } from "vitest";
import type { ProfileResponse } from "./api/profile";
import {
  experienceBracketLabel,
  resolvePeerConditions,
} from "./peerConditions";

const PROFILE: ProfileResponse = {
  jobFamily: "DEVELOPER",
  yearsOfExperience: 5,
  region: "서울",
  employmentType: "FULL_TIME",
  companySize: "LARGE",
};

const SERVER_TEXT = "서울 전체 / MID (4~7년) / LARGE / FULL_TIME";

describe("experienceBracketLabel", () => {
  it("서버 3구간 경계와 같다", () => {
    expect(experienceBracketLabel(0)).toBe("0~3년");
    expect(experienceBracketLabel(3)).toBe("0~3년");
    expect(experienceBracketLabel(4)).toBe("4~7년");
    expect(experienceBracketLabel(7)).toBe("4~7년");
    expect(experienceBracketLabel(8)).toBe("8년+");
  });
});

describe("resolvePeerConditions", () => {
  it("완화 1단계면 프로필로 한글 칩을 조립한다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 1, appliedConditions: SERVER_TEXT },
        PROFILE,
      ),
    ).toEqual({
      kind: "chips",
      chips: ["서울 전체", "4~7년", "대기업", "정규직"],
    });
  });

  it("회사규모가 없는 프로필은 2단계가 완전 일치다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 2, appliedConditions: SERVER_TEXT },
        { ...PROFILE, companySize: null },
      ),
    ).toEqual({
      kind: "chips",
      chips: ["서울 전체", "4~7년", "회사규모 무관", "정규직"],
    });
  });

  it("회사규모가 있는데 2단계면 완화다 — 서버 문자열을 그대로 쓴다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 2, appliedConditions: SERVER_TEXT },
        PROFILE,
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: true });
  });

  it("3단계 이상은 완화다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 4, appliedConditions: SERVER_TEXT },
        PROFILE,
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: true });
  });

  it("완화 단계를 모르면 완화로 본다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: null, appliedConditions: SERVER_TEXT },
        PROFILE,
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: true });
  });

  it("프로필을 못 받았으면 서버 문자열을 쓰되 완화라고 하지 않는다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 1, appliedConditions: SERVER_TEXT },
        null,
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: false });
  });

  // 서버는 고용형태가 비면 FULL_TIME 을 끼운다 — 빈 값으로 칩을 만들면 실제 조건과 다르다.
  it("칩에 필요한 프로필 값이 비면 서버 문자열로 물러난다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 1, appliedConditions: SERVER_TEXT },
        { ...PROFILE, employmentType: null },
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: false });
  });

  it("라벨 표에 없는 값이면 서버 문자열로 물러난다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 1, appliedConditions: SERVER_TEXT },
        { ...PROFILE, companySize: "UNKNOWN" },
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: false });
  });

  it("구 단위 지역에는 전체를 붙이지 않는다", () => {
    const view = resolvePeerConditions(
      { fallbackLevel: 1, appliedConditions: SERVER_TEXT },
      { ...PROFILE, region: "서울 강남구" },
    );
    expect(view).toMatchObject({
      kind: "chips",
      chips: ["서울 강남구", "4~7년", "대기업", "정규직"],
    });
  });

  it("동료집단 정보가 없으면 null", () => {
    expect(resolvePeerConditions(null, PROFILE)).toBeNull();
    expect(resolvePeerConditions(undefined, PROFILE)).toBeNull();
  });

  it('빈 회사규모는 null과 같이 2단계에서 "회사규모 무관"을 만든다', () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 2, appliedConditions: SERVER_TEXT },
        { ...PROFILE, companySize: "" },
      ),
    ).toEqual({
      kind: "chips",
      chips: ["서울 전체", "4~7년", "회사규모 무관", "정규직"],
    });
  });

  it("빈 지역이나 고용형태는 서버 문자열로 물러난다", () => {
    expect(
      resolvePeerConditions(
        { fallbackLevel: 1, appliedConditions: SERVER_TEXT },
        { ...PROFILE, region: "" },
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: false });

    expect(
      resolvePeerConditions(
        { fallbackLevel: 1, appliedConditions: SERVER_TEXT },
        { ...PROFILE, employmentType: "  " },
      ),
    ).toEqual({ kind: "server", text: SERVER_TEXT, widened: false });
  });
});
