import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { Button } from "./button";
import { ConfirmDialog } from "./confirm-dialog";

function renderDialog(onConfirm = vi.fn(), tone?: "danger" | "default") {
  render(
    <ConfirmDialog
      trigger={<Button variant="destructive">삭제</Button>}
      title="소득 이력을 삭제할까요?"
      description="되돌릴 수 없습니다."
      confirmLabel="삭제"
      tone={tone}
      onConfirm={onConfirm}
    />,
  );
  return { onConfirm, user: userEvent.setup() };
}

describe("ConfirmDialog", () => {
  it("열면 제목·설명을 보이고 포커스가 취소에 간다", async () => {
    const { user } = renderDialog();
    await user.click(screen.getByRole("button", { name: "삭제" }));
    const dialog = screen.getByRole("alertdialog", {
      name: "소득 이력을 삭제할까요?",
    });
    expect(dialog).toHaveAccessibleDescription("되돌릴 수 없습니다.");
    expect(screen.getByRole("button", { name: "취소" })).toHaveFocus();
  });

  it("실행 버튼은 onConfirm 을 한 번 부르고 닫힌다", async () => {
    const { user, onConfirm } = renderDialog();
    await user.click(screen.getByRole("button", { name: "삭제" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(
      Array.from(dialog.querySelectorAll("button")).find(
        (b) => b.textContent === "삭제",
      )!,
    );
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("취소·Esc 는 실행하지 않고 닫히며 포커스가 여는 버튼으로 돌아간다", async () => {
    const { user, onConfirm } = renderDialog();
    const trigger = screen.getByRole("button", { name: "삭제" });
    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(trigger).toHaveFocus();

    await user.click(trigger);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  // 위험 확인을 실수로 넘기지 않게 — 바깥 클릭은 닫지도 실행하지도 않는다.
  it("바깥(오버레이) 클릭은 닫지 않는다", async () => {
    const { user, onConfirm } = renderDialog();
    await user.click(screen.getByRole("button", { name: "삭제" }));
    // 열린 동안 뒤 화면은 pointer-events: none 이라 userEvent 가 누를 수 없다 — 오버레이에 직접 보낸다.
    const overlay = document.querySelector(
      "[data-slot=confirm-overlay]",
    ) as HTMLElement;
    fireEvent.pointerDown(overlay);
    fireEvent.click(overlay);
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("tone 에 따라 실행 버튼 모양이 갈린다", async () => {
    const { user } = renderDialog(vi.fn(), "default");
    await user.click(screen.getByRole("button", { name: "삭제" }));
    const dialog = screen.getByRole("alertdialog");
    const action = Array.from(dialog.querySelectorAll("button")).find(
      (b) => b.textContent === "삭제",
    )!;
    expect(action).toHaveAttribute("data-variant", "default");
    expect(screen.getByRole("button", { name: "취소" })).toHaveAttribute(
      "data-variant",
      "outline",
    );
  });

  it("기본 tone 은 destructive 다", async () => {
    const { user } = renderDialog();
    await user.click(screen.getByRole("button", { name: "삭제" }));
    const action = Array.from(
      screen.getByRole("alertdialog").querySelectorAll("button"),
    ).find((b) => b.textContent === "삭제")!;
    expect(action).toHaveAttribute("data-variant", "destructive");
  });

  // 실패하면 trigger 가 다시 살아난다 — 그때 포커스가 body 가 아니라 trigger 로 돌아와야 키보드 사용자가 길을 잃지 않는다.
  it("확인 뒤 처리 중 막혔던 trigger 가 다시 살아나면 포커스가 trigger 로 돌아온다", async () => {
    function Harness() {
      const [pending, setPending] = useState(false);
      return (
        <>
          <ConfirmDialog
            trigger={
              <Button variant="destructive" disabled={pending}>
                삭제
              </Button>
            }
            title="소득 이력을 삭제할까요?"
            description="되돌릴 수 없습니다."
            confirmLabel="삭제"
            onConfirm={() => setPending(true)}
          />
          <Button onClick={() => setPending(false)}>실패로 끝내기</Button>
        </>
      );
    }
    render(<Harness />);
    const user = userEvent.setup();
    const trigger = screen.getByRole("button", { name: "삭제" });
    await user.click(trigger);
    const dialog = screen.getByRole("alertdialog");
    await user.click(
      Array.from(dialog.querySelectorAll("button")).find(
        (b) => b.textContent === "삭제",
      )!,
    );
    expect(trigger).toBeDisabled();
    // 요청 실패 — 사용자 동작이 아니라 상태가 풀린다(포커스를 옮기지 않는 경로로).
    fireEvent.click(screen.getByRole("button", { name: "실패로 끝내기" }));
    await waitFor(() => expect(trigger).toHaveFocus());
  });
});
