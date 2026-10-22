import { describe, expect, it } from "vitest";

import { isApiError, toUserMessage } from "./apiError";

describe("toUserMessage", () => {
  it("정규화된 ApiError 는 그 message 를 그대로 쓴다", () => {
    const error = {
      code: "SERVER_ERROR",
      message: "서버 오류가 발생했습니다.",
      status: 500,
    };

    expect(toUserMessage(error)).toBe("서버 오류가 발생했습니다.");
  });

  it("ApiError 모양이 아닌 오류는 code·status 를 노출하지 않고 고정 문구로 떨어진다", () => {
    expect(toUserMessage(new TypeError("network down"))).toBe(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
    expect(toUserMessage("string error")).toBe(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
    expect(toUserMessage(null)).toBe(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  });
});

// income.tsx 의 409(중복 유효 시작일) 판별과 simulator.tsx 의 400(소득 이력 없음)
// 판별이 이 판별자 하나를 공유한다 — 각자 status 만 덧붙여 좁힌다(apiError.ts).
describe("isApiError", () => {
  it("셋 중 하나라도 없으면 ApiError 가 아니다", () => {
    expect(isApiError({ message: "중복", status: 409 })).toBe(false);
    expect(isApiError({ code: "CONFLICT", status: 409 })).toBe(false);
    expect(isApiError({ code: "CONFLICT", message: "중복" })).toBe(false);
  });
});
