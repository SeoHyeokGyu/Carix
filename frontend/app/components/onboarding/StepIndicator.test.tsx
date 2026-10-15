import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";

import { resetSession, setSession } from "../../lib/session";
import { StepIndicator } from "./StepIndicator";

describe("StepIndicator (온보딩 단계 인디케이터)", () => {
  beforeEach(() => {
    resetSession();
  });

  it("현재 단계 및 이전 단계들을 올바르게 시각화한다", () => {
    render(
      <MemoryRouter initialEntries={["/onboarding/expense"]}>
        <StepIndicator current={3} />
      </MemoryRouter>,
    );

    const nav = screen.getByRole("navigation", { name: "온보딩 단계" });
    expect(nav).toBeInTheDocument();

    // 1(프로필), 2(소득) 단계는 이전 완료 단계이므로 링크로 렌더링
    const profileLink = screen.getByRole("link", { name: /프로필/ });
    const incomeLink = screen.getByRole("link", { name: /소득/ });

    expect(profileLink).toBeInTheDocument();
    expect(profileLink).toHaveAttribute("href", "/onboarding/profile");
    expect(incomeLink).toBeInTheDocument();
    expect(incomeLink).toHaveAttribute("href", "/onboarding/income");

    // 3(소비) 단계는 현재 단계이므로 aria-current="step"
    const expenseItem = screen.getByRole("listitem", { current: "step" });
    expect(expenseItem).toHaveTextContent("소비");

    // 4(스킬), 5(검토) 단계는 아직 도달하지 않아 링크가 아님
    expect(
      screen.queryByRole("link", { name: /스킬/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /검토/ }),
    ).not.toBeInTheDocument();
  });

  it("세션에 더 앞선 단계(예: SKILLS)가 기록되어 있는 경우 해당 단계까지 링크가 활성화된다", () => {
    setSession({
      status: "authenticated",
      userId: "u-1",
      email: "test@carix.kr",
      onboardingStep: "SKILLS",
    });

    render(
      <MemoryRouter initialEntries={["/onboarding/profile"]}>
        <StepIndicator current={1} />
      </MemoryRouter>,
    );

    // 현재는 1(프로필)이지만, 세션이 SKILLS(4단계)까지 열려 있으므로 2, 3, 4단계 링크가 활성화됨
    expect(screen.getByRole("link", { name: /소득/ })).toHaveAttribute(
      "href",
      "/onboarding/income",
    );
    expect(screen.getByRole("link", { name: /소비/ })).toHaveAttribute(
      "href",
      "/onboarding/expense",
    );
    expect(screen.getByRole("link", { name: /스킬/ })).toHaveAttribute(
      "href",
      "/onboarding/skills",
    );

    // 5단계(검토)는 아직 미도달이므로 비활성화
    expect(
      screen.queryByRole("link", { name: /검토/ }),
    ).not.toBeInTheDocument();
  });
});
