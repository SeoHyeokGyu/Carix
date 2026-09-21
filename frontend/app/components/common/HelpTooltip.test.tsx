import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { HelpTooltip } from "./HelpTooltip";

describe("HelpTooltip", () => {
  it("버튼을 클릭하면 툴팁 도움말이 열리고 닫힌다", async () => {
    const user = userEvent.setup();
    render(
      <HelpTooltip label="테스트 지표">
        <p>도움말 상세 내용입니다.</p>
      </HelpTooltip>,
    );

    const button = screen.getByRole("button", { name: "테스트 지표 도움말" });
    expect(button).toBeInTheDocument();
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();

    // 클릭 시 열림
    await user.click(button);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();
    expect(screen.getByText("도움말 상세 내용입니다.")).toBeInTheDocument();

    // 다시 클릭 시 닫힘
    await user.click(button);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("마우스를 올리면 열리고 벗어나면 닫힌다", async () => {
    const user = userEvent.setup();
    render(
      <HelpTooltip label="테스트 지표">
        <p>도움말 상세 내용입니다.</p>
      </HelpTooltip>,
    );

    const button = screen.getByRole("button", { name: "테스트 지표 도움말" });
    await user.hover(button);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();

    await user.unhover(button);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("바깥 영역을 클릭하면 툴팁이 닫힌다", async () => {
    const user = userEvent.setup();
    render(
      <div>
        <span data-testid="outside">바깥 영역</span>
        <HelpTooltip label="테스트 지표">
          <p>도움말 상세 내용입니다.</p>
        </HelpTooltip>
      </div>,
    );

    const button = screen.getByRole("button", { name: "테스트 지표 도움말" });
    await user.click(button);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();

    await user.click(screen.getByTestId("outside"));
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("ESC 키를 누르면 툴팁이 닫힌다", async () => {
    const user = userEvent.setup();
    render(
      <HelpTooltip label="테스트 지표">
        <p>도움말 상세 내용입니다.</p>
      </HelpTooltip>,
    );

    const button = screen.getByRole("button", { name: "테스트 지표 도움말" });
    await user.click(button);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });
});
