import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { IncomeResponse } from "../../lib/api/income";
import { BonusTermsForm } from "./BonusTermsForm";

const incomes: IncomeResponse[] = [
  {
    id: "i-new",
    grossSalary: 65_000_000,
    netSalary: 4_200_000,
    effectiveDate: "2024-01-01",
    changeReason: "RAISE",
    selectedChangeReason: "RAISE",
    bonusIncluded: true,
    fixedBonusAnnual: null,
  },
  {
    id: "i-old",
    grossSalary: 58_000_000,
    netSalary: 3_800_000,
    effectiveDate: "2023-01-01",
    changeReason: "INITIAL",
    selectedChangeReason: null,
    bonusIncluded: false,
    fixedBonusAnnual: 12_000_000,
  },
];

function renderForm(onSubmit = vi.fn()) {
  render(
    <BonusTermsForm
      incomes={incomes}
      onSubmit={onSubmit}
      isSubmitting={false}
    />,
  );
  return onSubmit;
}

const AMOUNT_LABEL = /연간 고정상여/;

describe("BonusTermsForm", () => {
  it("따로 받음을 고를 때만 연간 고정상여 칸이 보인다", async () => {
    renderForm();
    const user = userEvent.setup();

    expect(
      screen.getByRole("group", {
        name: "연봉 계약 금액에 상여가 들어 있나요?",
      }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("들어 있음")).toBeChecked();
    expect(screen.queryByLabelText(AMOUNT_LABEL)).not.toBeInTheDocument();

    await user.click(screen.getByLabelText("따로 받음"));
    expect(screen.getByLabelText(AMOUNT_LABEL)).toBeInTheDocument();

    await user.click(screen.getByLabelText("들어 있음"));
    expect(screen.queryByLabelText(AMOUNT_LABEL)).not.toBeInTheDocument();
  });

  it("들어 있음 제출은 고정상여 null 로 보낸다", async () => {
    const onSubmit = renderForm();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "상여 조건 저장" }));

    expect(onSubmit).toHaveBeenCalledWith({
      applyFromIncomeId: "i-new",
      bonusIncluded: true,
      fixedBonusAnnual: null,
    });
  });

  it("따로 받음 제출은 만원 입력을 원 단위로 환산해 보낸다", async () => {
    const onSubmit = renderForm();
    const user = userEvent.setup();

    await user.selectOptions(
      screen.getByLabelText(/이 연봉부터 적용/),
      "i-new",
    );
    await user.click(screen.getByLabelText("따로 받음"));
    await user.type(screen.getByLabelText(AMOUNT_LABEL), "1200");
    await user.click(screen.getByRole("button", { name: "상여 조건 저장" }));

    expect(onSubmit).toHaveBeenCalledWith({
      applyFromIncomeId: "i-new",
      bonusIncluded: false,
      fixedBonusAnnual: 12_000_000,
    });
  });

  it("따로 받음인데 금액이 비어 있으면 보내지 않고 안내한다", async () => {
    const onSubmit = renderForm();
    const user = userEvent.setup();

    await user.click(screen.getByLabelText("따로 받음"));
    await user.click(screen.getByRole("button", { name: "상여 조건 저장" }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "연간 고정상여 금액을 입력해 주세요",
    );
  });

  it("서버가 돌려준 필드 오류를 금액 칸에 붙인다", async () => {
    render(
      <BonusTermsForm
        incomes={incomes}
        onSubmit={vi.fn()}
        isSubmitting={false}
        errors={{
          fixedBonusAnnual: "고정상여는 따로 받을 때만 입력할 수 있습니다.",
        }}
      />,
    );
    const user = userEvent.setup();
    await user.click(screen.getByLabelText("따로 받음"));

    expect(screen.getByLabelText(AMOUNT_LABEL)).toHaveAccessibleDescription(
      expect.stringContaining("따로 받을 때만"),
    );
  });
});
