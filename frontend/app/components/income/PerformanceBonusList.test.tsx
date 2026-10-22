import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { BonusResponse } from "../../lib/api/income";
import { confirmDialog } from "../../test-utils/confirmDialog";
import { PerformanceBonusList } from "./PerformanceBonusList";

const bonuses: BonusResponse[] = [
  { id: "b-2", paidMonth: "202603", amount: 5_000_000 },
  { id: "b-1", paidMonth: "202503", amount: 3_000_000 },
];

function renderList(
  props: Partial<React.ComponentProps<typeof PerformanceBonusList>> = {},
) {
  const onCreate = vi.fn();
  const onDelete = vi.fn();
  render(
    <PerformanceBonusList
      bonuses={bonuses}
      onCreate={onCreate}
      onDelete={onDelete}
      isSubmitting={false}
      {...props}
    />,
  );
  return { onCreate, onDelete };
}

describe("PerformanceBonusList", () => {
  it("추가는 지급월을 YYYYMM, 금액을 원 단위로 환산해 보낸다", async () => {
    const { onCreate } = renderList();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("지급월"), "2026-04");
    await user.type(screen.getByLabelText(/금액/), "700");
    await user.click(screen.getByRole("button", { name: "성과급 추가" }));

    expect(onCreate).toHaveBeenCalledWith({
      paidMonth: "202604",
      amount: 7_000_000,
    });
  });

  it("지급월이나 금액이 비면 보내지 않고 안내한다", async () => {
    const { onCreate } = renderList();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "성과급 추가" }));

    expect(onCreate).not.toHaveBeenCalled();
    expect(screen.getAllByRole("alert").length).toBeGreaterThan(0);
  });

  it("삭제는 확인창을 거친다", async () => {
    const { onDelete } = renderList();
    const user = userEvent.setup();

    await user.click(screen.getAllByRole("button", { name: "삭제" })[0]);
    expect(onDelete).not.toHaveBeenCalled();
    await confirmDialog(user, "삭제");

    expect(onDelete).toHaveBeenCalledWith("b-2");
  });
});
