import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PriceDriverBadge } from "./PriceDriverBadge";

const DRIVER = {
  category: "TRANSPORT",
  label: "교통 부담률 최대 상승",
  estimated: false,
  cpiMonth: "202609",
};

describe("PriceDriverBadge", () => {
  it("서버 라벨을 그대로 보인다", () => {
    render(<PriceDriverBadge driver={DRIVER} />);
    expect(screen.getByText("교통 부담률 최대 상승")).toBeInTheDocument();
  });

  it("기본 가중치로 채운 카테고리면 추정을 붙인다", () => {
    render(<PriceDriverBadge driver={{ ...DRIVER, estimated: true }} />);
    expect(
      screen.getByText("교통 부담률 최대 상승 · 추정"),
    ).toBeInTheDocument();
  });

  it("값이 없으면 아무것도 그리지 않는다", () => {
    const { container } = render(<PriceDriverBadge driver={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});
