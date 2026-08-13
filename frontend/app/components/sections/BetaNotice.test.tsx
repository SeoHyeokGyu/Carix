import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { BetaNotice } from "./BetaNotice";

describe("BetaNotice", () => {
  it("베타 안내 문구를 렌더링한다", () => {
    render(<BetaNotice />);
    expect(
      screen.getByText(/초대받은 10~30명의 비공개 베타 사용자/),
    ).toBeInTheDocument();
  });

  it("버튼이나 링크(CTA)를 렌더링하지 않는다", () => {
    render(<BetaNotice />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
