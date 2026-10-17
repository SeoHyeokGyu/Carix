import { describe, expect, it } from "vitest";

import { resolvePeerConditions } from "./peerConditions";

const SERVER_TEXT = "전국 / 4~7년 / 회사규모 무관 / 고용형태 무관";

describe("resolvePeerConditions", () => {
  // 서버 문구가 실제 조회 조건이다. 프로필로 칩을 다시 만들면 집계에 없는
  // 지역·규모·고용형태를 비교한 것처럼 보인다.
  it("1단계면 서버 문구를 그대로 쓰고 넓혔다고 하지 않는다", () => {
    expect(
      resolvePeerConditions({
        fallbackLevel: 1,
        appliedConditions: SERVER_TEXT,
      }),
    ).toEqual({ text: SERVER_TEXT, widened: false });
  });

  it("2단계면 넓혀 비교했다고 표시한다", () => {
    expect(
      resolvePeerConditions({
        fallbackLevel: 2,
        appliedConditions: SERVER_TEXT,
      }),
    ).toEqual({ text: SERVER_TEXT, widened: true });
  });

  it("완화 단계를 모르면 넓힌 것으로 본다", () => {
    expect(
      resolvePeerConditions({
        fallbackLevel: null,
        appliedConditions: SERVER_TEXT,
      }),
    ).toEqual({ text: SERVER_TEXT, widened: true });
  });

  it("서버 문구에 영문 구간·규모·고용형태 코드가 섞여 있으면 한국어로 변환한다", () => {
    expect(
      resolvePeerConditions({
        fallbackLevel: 1,
        appliedConditions: "서울 전체 / 경력 MID / LARGE / FULL_TIME",
      }),
    ).toEqual({
      text: "서울 전체 / 경력 미들 / 대기업 / 정규직",
      widened: false,
    });
  });

  it("동료집단 정보가 없으면 null", () => {
    expect(resolvePeerConditions(null)).toBeNull();
    expect(resolvePeerConditions(undefined)).toBeNull();
  });
});
