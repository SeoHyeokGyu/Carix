import { screen, within } from "@testing-library/react";
import type { UserEvent } from "@testing-library/user-event";

/** 열린 확인창 안의 실행 버튼을 누른다 — 같은 이름의 행 버튼과 헷갈리지 않게 확인창 안에서만 찾는다. */
export async function confirmDialog(
  user: UserEvent,
  confirmLabel: string,
): Promise<void> {
  const dialog = await screen.findByRole("alertdialog");
  await user.click(within(dialog).getByRole("button", { name: confirmLabel }));
}

export async function cancelDialog(user: UserEvent): Promise<void> {
  const dialog = await screen.findByRole("alertdialog");
  await user.click(within(dialog).getByRole("button", { name: "취소" }));
}
