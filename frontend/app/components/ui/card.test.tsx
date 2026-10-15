import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Card, CardTitle } from "./card";

describe("CardTitle", () => {
  it("기본은 소제목 h3 다", () => {
    render(<CardTitle>지표</CardTitle>);
    const h = screen.getByRole("heading", { level: 3, name: "지표" });
    expect(h).toHaveClass("font-display", "text-sm", "font-semibold");
    expect(h).toHaveAttribute("data-slot", "card-title");
    // text-sm 이 line-height 까지 정해 앞에 둔 leading-* 를 tailwind-merge 가 지운다 — 뒤에 둬야 남는다.
    expect(h).toHaveClass("leading-snug");
  });

  it("as h2 는 섹션 제목 단계다", () => {
    render(<CardTitle as="h2">세션 관리</CardTitle>);
    expect(
      screen.getByRole("heading", { level: 2, name: "세션 관리" }),
    ).toHaveClass("text-lg", "font-semibold");
  });

  it("className 을 합친다", () => {
    render(<CardTitle className="text-destructive">탈퇴</CardTitle>);
    expect(screen.getByRole("heading")).toHaveClass("text-destructive");
  });

  it("Card 는 data-page-section 같은 속성을 넘긴다", () => {
    render(<Card data-page-section="" data-testid="c" />);
    expect(screen.getByTestId("c")).toHaveAttribute("data-page-section");
  });
});
