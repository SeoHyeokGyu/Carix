import { describe, expect, it } from "vitest";
import { toUserMessage } from "./apiError";

describe("toUserMessage", () => {
  it("정규화된 ApiError 는 그 message 를 그대로 쓴다", () => {
    const error = { code: "SERVER_ERROR", message: "서버 오류가 발생했습니다.", status: 500 };

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
