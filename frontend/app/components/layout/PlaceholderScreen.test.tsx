import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PlaceholderScreen } from "./PlaceholderScreen";

describe("PlaceholderScreen", () => {
  it("renders screenId, title, description, and placeholder message", () => {
    render(
      <PlaceholderScreen
        screenId="S-99"
        title="테스트 타이틀"
        description="테스트 설명글입니다."
      />,
    );

    expect(screen.getByText("S-99")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "테스트 타이틀" }),
    ).toBeInTheDocument();
    expect(screen.getByText("테스트 설명글입니다.")).toBeInTheDocument();
    expect(
      screen.getByText(/라우트 골격만 있는 화면입니다/),
    ).toBeInTheDocument();
  });
});
