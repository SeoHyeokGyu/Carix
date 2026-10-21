import { render, screen, within } from "@testing-library/react";
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
  it("비어 있으면 기록을 유도하는 문구를 보인다", () => {
    renderList({ bonuses: [] });
    expect(
      screen.getByText("받은 성과급이 있으면 지급월과 금액을 기록해 두세요"),
    ).toBeInTheDocument();
  });

  it("지급월과 금액을 표로 보인다", () => {
    renderList();
    const rows = screen.getAllByRole("row");
    expect(within(rows[1]).getByText("2026-03")).toBeInTheDocument();
    expect(within(rows[1]).getByText("500만 원")).toBeInTheDocument();
    expect(within(rows[2]).getByText("2025-03")).toBeInTheDocument();
  });

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

  it("지급월 입력은 미래 달을 고를 수 없다", () => {
    renderList();
    const max = screen.getByLabelText("지급월").getAttribute("max");
    expect(max).toMatch(/^\d{4}-\d{2}$/);
  });
});
