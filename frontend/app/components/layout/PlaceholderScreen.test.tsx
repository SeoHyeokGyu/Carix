import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PlaceholderScreen } from "./PlaceholderScreen";

describe("PlaceholderScreen", () => {
  it("화면 ID, 제목, 설명글 및 플레이스홀더 안내 문구를 렌더링한다", () => {
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
