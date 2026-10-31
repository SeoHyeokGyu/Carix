import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ScenarioTemplateMenu } from "./ScenarioTemplateMenu";

describe("ScenarioTemplateMenu", () => {
  it("메뉴 버튼을 클릭하면 템플릿 목록 팝오버를 열고 템플릿들을 노출한다", async () => {
    const onSelect = vi.fn();
    render(
      <ScenarioTemplateMenu currentSalary={60_000_000} onSelect={onSelect} />,
    );

    const trigger = screen.getByRole("button", { name: /시나리오 템플릿/ });
    expect(trigger).toBeInTheDocument();

    await userEvent.click(trigger);

    expect(screen.getByText("안정적 연봉 점프")).toBeInTheDocument();
    expect(screen.getByText("스타트업 보상 (스톡·사이닝)")).toBeInTheDocument();
    expect(screen.getByText("충전 후 이직 (2개월 공백)")).toBeInTheDocument();
    expect(screen.getByText("워라밸 수평 이동")).toBeInTheDocument();
  });

  it("특정 템플릿의 적용 버튼을 선택하면 onSelect 콜백이 호출된다", async () => {
    const onSelect = vi.fn();
    render(
      <ScenarioTemplateMenu currentSalary={60_000_000} onSelect={onSelect} />,
    );

    await userEvent.click(
      screen.getByRole("button", { name: /시나리오 템플릿/ }),
    );

    const applyButtons = screen.getAllByRole("button", { name: "적용" });
    await userEvent.click(applyButtons[0]);

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ id: "jump", name: "안정적 연봉 점프" }),
    );
  });
});
