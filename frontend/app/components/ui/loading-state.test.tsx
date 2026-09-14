import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LoadingState } from "./loading-state";

describe("LoadingState", () => {
  it("메시지를 보여준다", () => {
    render(<LoadingState message="스냅샷 이력을 조회하고 있습니다..." />);
    expect(
      screen.getByText("스냅샷 이력을 조회하고 있습니다..."),
    ).toBeInTheDocument();
  });

  it("보조 기술에 로딩 중임을 알린다", () => {
    render(<LoadingState message="조회 중" />);
    const region = screen.getByRole("status");
    expect(region).toHaveAttribute("aria-busy", "true");
  });
});
