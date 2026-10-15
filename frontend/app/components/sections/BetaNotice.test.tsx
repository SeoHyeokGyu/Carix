import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";

import { BetaNotice } from "./BetaNotice";

function renderNotice() {
  render(
    <MemoryRouter>
      <BetaNotice />
    </MemoryRouter>,
  );
}

describe("BetaNotice", () => {
  it("안내 문구를 렌더링한다", () => {
    renderNotice();
    expect(
      screen.getByText(/이메일과 비밀번호만으로 가입해 바로 써 볼 수 있습니다/),
    ).toBeInTheDocument();
  });

  it("가입과 로그인 진입점을 제공한다", () => {
    renderNotice();
    expect(
      screen.getByRole("link", { name: "가입하고 시작하기" }),
    ).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
  });
});
