import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import Home from "./home";

describe("Home route", () => {
  it("모든 섹션 헤딩을 문서 순서대로 렌더링한다", () => {
    render(
      <MemoryRouter>
        <Home />
      </MemoryRouter>,
    );
    const headings = screen
      .getAllByRole("heading", { level: 1 })
      .concat(screen.getAllByRole("heading", { level: 2 }))
      .map((h) => h.textContent);

    expect(headings).toEqual([
      "Carix",
      "핵심 지수",
      "데이터 연결 구조",
      "계산 방식",
      "주요 기능",
      "결과 예시",
      "시작하기",
    ]);
  });

  it("가입과 로그인 진입점을 제공한다", () => {
    render(
      <MemoryRouter>
        <Home />
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("link", { name: "가입하고 시작하기" }),
    ).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
  });
});
