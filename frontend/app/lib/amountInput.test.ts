import { describe, expect, it } from "vitest";

import {
  formatAmountInput,
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
