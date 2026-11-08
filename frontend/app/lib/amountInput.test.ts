import { describe, expect, it } from "vitest";

import {
  formatAmountInput,
  keepUntouchedAmount,
  parseAmountInput,
  withPlainAmounts,
} from "./amountInput";

describe("formatAmountInput", () => {
  it("숫자 외 문자를 버리고 천 단위 콤마를 붙인다", () => {
    expect(formatAmountInput("72000000")).toBe("72,000,000");
    expect(formatAmountInput("7,2a00")).toBe("7,200");
  });

  it("0 은 유효한 값으로 남긴다", () => {
    expect(formatAmountInput("0")).toBe("0");
  });
});

describe("parseAmountInput", () => {
  it("콤마를 걷어내고 숫자로 읽는다", () => {
    expect(parseAmountInput("350,000")).toBe(350_000);
  });
});

describe("withPlainAmounts", () => {
  it("지정한 필드만 콤마를 걷어내고 배율을 곱한다", () => {
    const formData = new FormData();
    formData.append("moveTargetGrossSalary", "7,200");
    formData.append("scenarioName", "A사, 오퍼");

    const next = withPlainAmounts(formData, ["moveTargetGrossSalary"], 10_000);

    expect(next.get("moveTargetGrossSalary")).toBe("72000000");
    expect(next.get("scenarioName")).toBe("A사, 오퍼");
  });

  it("빈 칸은 빈 문자열로 남겨 미입력으로 처리되게 한다", () => {
    const formData = new FormData();
    formData.append("FOOD", "");

    expect(withPlainAmounts(formData, ["FOOD"]).get("FOOD")).toBe("");
  });
});

describe("keepUntouchedAmount", () => {
  // 화면은 만원 단위라 원 단위로 저장된 옛 값을 반올림해 보인다. 손대지 않은 칸까지 반올림 값으로
  // 다시 저장하면 사용자 몰래 데이터가 바뀐다 — 200원은 0원이 되어 그 달이 완전 입력에서 빠진다.
  it("반올림해 보인 값을 그대로 두면 원래 원 단위 값을 보낸다", () => {
    expect(keepUntouchedAmount(0, 200)).toBe(200);
    expect(keepUntouchedAmount(1_230_000, 1_234_567)).toBe(1_234_567);
  });

  it("값을 고쳤거나 원래 값이 없으면 입력한 값을 보낸다", () => {
    expect(keepUntouchedAmount(1_240_000, 1_234_567)).toBe(1_240_000);
    expect(keepUntouchedAmount(500_000, null)).toBe(500_000);
  });
});
