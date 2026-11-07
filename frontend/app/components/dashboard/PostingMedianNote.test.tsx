import { describe, expect, it } from "vitest";

import type { SalaryDistributionResponse } from "../../lib/dashboardMarketApi";
import { postingMedianText } from "./PostingMedianNote";

const reference = {
  min: 30_000_000,
  p25: 45_000_000,
  median: 55_500_000,
  p75: 65_000_000,
  max: 135_000_000,
  sampleSize: 87,
  missingRate: 97.7,
};

describe("PostingMedianNote", () => {
  it("표본이 부족하면 참고 줄을 숨긴다", () => {
    // 공개연봉 결측이 90% 넘는 공고 중앙값을 표본 부족인데도 보이면 거짓 정밀도가 된다.
    const insufficient: SalaryDistributionResponse = {
      status: "INSUFFICIENT_SAMPLE",
      sampleSize: 4,
      postingReference: { ...reference, sampleSize: 4 },
    };

    expect(postingMedianText(insufficient, 58_000_000)).toBeNull();
    expect(postingMedianText(null, 58_000_000)).toBeNull();
    expect(
      postingMedianText(
        { status: "OK", sampleSize: 87, postingReference: reference },
        null,
      ),
    ).toBeNull();
  });

  it("내 연봉이 공고 중앙값보다 크면 높음으로 말한다", () => {
    // 부호가 뒤집히면 "덜 받는데 높음"이 된다 — 방향만 고정한다.
    expect(
      postingMedianText(
        { status: "OK", sampleSize: 87, postingReference: reference },
        58_000_000,
      ),
    ).toContain("4.5% 높음");
  });
});
