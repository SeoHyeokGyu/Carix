import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Sparkline, sparklineDomain } from "./Sparkline";

function series(values: number[]) {
  return {
    slots: values.length,
    segments: [values.map((value, index) => ({ index, value }))],
  };
}

describe("sparklineDomain", () => {
  it("100 을 가운데 둔 대칭 범위이고 최소 반폭은 10 이다", () => {
    expect(sparklineDomain([100, 105.2])).toEqual({ min: 90, max: 110 });
  });

  it("100 에서 더 멀리 벗어나면 그만큼 넓힌다", () => {
    expect(sparklineDomain([82, 104])).toEqual({ min: 82, max: 118 });
  });

  it("기준선이 없으면(금액) 최소·최대로 채운다", () => {
    expect(sparklineDomain([48_000_000, 52_000_000], null)).toEqual({
      min: 48_000_000,
      max: 52_000_000,
    });
  });

  it("기준선 없이 값이 모두 같아도 범위가 0 이 되지 않는다", () => {
    const { min, max } = sparklineDomain([50_000_000, 50_000_000], null);
    expect(max - min).toBeGreaterThan(0);
  });
});

describe("Sparkline", () => {
  it("기준선이 없으면 점선을 긋지 않는다", () => {
    const { container } = render(
      <Sparkline series={series([48_000_000, 52_000_000])} baseline={null} />,
    );
    expect(
      container.querySelector("[data-testid='sparkline-baseline']"),
    ).toBeNull();
    expect(container.querySelector("polyline")).not.toBeNull();
  });

  it("기준 100 에 점선 기준선을 그린다", () => {
    const { container } = render(<Sparkline series={series([100, 105])} />);
    const baseline = container.querySelector(
      "[data-testid='sparkline-baseline']",
    );
    expect(baseline).not.toBeNull();
    // 높이 32, 기준선은 대칭 범위의 가운데
    expect(baseline!.getAttribute("y1")).toBe("16");
  });

  // 최소·최대로 높이를 채우면 5% 변화가 바닥에서 천장으로 치솟아 보인다.
  it("100 → 105 는 높이 전체를 채우지 않는다", () => {
    const { container } = render(<Sparkline series={series([100, 105])} />);
    const ys = container
      .querySelector("polyline")!
      .getAttribute("points")!
      .split(" ")
      .map((pair) => Number(pair.split(",")[1]));
    expect(Math.abs(ys[0] - ys[1])).toBeLessThan(10);
  });
});
