import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

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

  describe("좁은 화면에서 화면 밖으로 나가지 않는다", () => {
    function mockTooltipRect(left: number, width: number) {
      vi.spyOn(
        HTMLElement.prototype,
        "getBoundingClientRect",
      ).mockImplementation(function (this: HTMLElement) {
        if (this.getAttribute("role") !== "tooltip") return new DOMRect();
        return new DOMRect(left, 0, width, 100);
      });
    }

    afterEach(() => {
      vi.restoreAllMocks();
      vi.unstubAllGlobals();
    });

    async function openTooltip() {
      render(
        <HelpTooltip label="테스트 지표">
          <p>내용</p>
        </HelpTooltip>,
      );
      await userEvent
        .setup()
        .click(screen.getByRole("button", { name: "테스트 지표 도움말" }));
      return screen.getByRole("tooltip");
    }

    it("오른쪽으로 넘치면 넘친 만큼 왼쪽으로 민다", async () => {
      vi.stubGlobal("innerWidth", 375);
      mockTooltipRect(129, 256); // 오른쪽 끝 385 — 375 에서 여백 16 을 빼면 26 넘친다

      const tooltip = await openTooltip();

      expect(tooltip.style.translate).toBe("-26px 0");
    });

    it("왼쪽으로 넘치면 넘친 만큼 오른쪽으로 민다", async () => {
      vi.stubGlobal("innerWidth", 1024);
      mockTooltipRect(-40, 256);

      const tooltip = await openTooltip();

      expect(tooltip.style.translate).toBe("56px 0");
    });

    it("화면 안에 있으면 옮기지 않는다", async () => {
      vi.stubGlobal("innerWidth", 1024);
      mockTooltipRect(129, 256);

      const tooltip = await openTooltip();

      expect(tooltip.style.translate).toBe("");
    });

    it("화면보다 넓어지지 않게 최대 폭을 둔다", async () => {
      const tooltip = await openTooltip();
      expect(tooltip).toHaveClass("max-w-[calc(100vw-2rem)]");
    });
  });

  // Button 의 icon-xs 기본값이 아이콘을 12px 로 줄이고 다크 hover 에 배경을 깔았다.
  it("아이콘 크기를 지키고 다크 모드에서도 hover 배경을 깔지 않는다", () => {
    render(
      <HelpTooltip label="테스트 지표">
        <p>내용</p>
      </HelpTooltip>,
    );
    const button = screen.getByRole("button", { name: "테스트 지표 도움말" });
    expect(button).toHaveClass("dark:hover:bg-transparent");
    expect(button).not.toHaveClass("dark:hover:bg-accent/50");
    expect(button.querySelector("svg")).toHaveClass("size-3.5");
  });
});
