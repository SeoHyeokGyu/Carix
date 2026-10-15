import { AlertDialog } from "radix-ui";
import { type ReactNode, useRef } from "react";

import { Button } from "./button";
import { HEADING } from "./heading";

export type ConfirmDialogProps = {
  /** 확인창을 여는 버튼. asChild 로 그대로 쓴다 — 처리 중이면 호출부가 disabled 로 막는다. */
  trigger: ReactNode;
  title: string;
  /** 무엇이 사라지고 어떤 영향이 있는지 한 줄. */
  description: ReactNode;
  confirmLabel: string;
  /** 데이터를 지우면 danger(기본), 지우지 않는 확인(초기화)은 default. */
  tone?: "danger" | "default";
  onConfirm: () => void;
};

/**
 * 되돌리기 어려운 동작의 확인창. 포커스는 「취소」로 가고 Esc·「취소」로만 닫힌다 —
 * 바깥 클릭으로 닫지 않는 것은 AlertDialog 의 기본이다(실수로 넘기지 않게).
 */
export function ConfirmDialog({
  trigger,
  title,
  description,
  confirmLabel,
  tone = "danger",
  onConfirm,
}: ConfirmDialogProps) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  return (
    <AlertDialog.Root>
      <AlertDialog.Trigger asChild ref={triggerRef}>
        {trigger}
      </AlertDialog.Trigger>
      <AlertDialog.Portal>
        <AlertDialog.Overlay
          data-slot="confirm-overlay"
          className="fixed inset-0 z-50 bg-black/50"
        />
        <AlertDialog.Content
          onCloseAutoFocus={(event) =>
            returnFocusWhenEnabled(event, triggerRef.current)
          }
          className="fixed top-1/2 left-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border border-carix-border bg-carix-surface p-6 text-carix-text shadow-lg"
        >
          <AlertDialog.Title className={HEADING.section}>
            {title}
          </AlertDialog.Title>
          <AlertDialog.Description className="mt-2 text-sm text-carix-text/70">
            {description}
          </AlertDialog.Description>
          <div className="mt-6 flex flex-wrap justify-end gap-3">
            <AlertDialog.Cancel asChild>
              <Button variant="outline">취소</Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button
                variant={tone === "danger" ? "destructive" : "default"}
                onClick={onConfirm}
              >
                {confirmLabel}
              </Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}

const FOCUS_RETURN_TIMEOUT_MS = 30_000;

/**
 * 「실행」 뒤에는 호출부가 처리 중 trigger 를 disabled 로 막아, Radix 가 닫으며 돌려주는
 * 포커스가 body 로 빠진다. 실패해 trigger 가 다시 살아나면 그때 포커스를 돌려준다 —
 * 성공해 trigger 가 사라지면(행 삭제) 아무것도 하지 않는다.
 */
function returnFocusWhenEnabled(
  event: Event,
  trigger: HTMLButtonElement | null,
) {
  if (!trigger || !trigger.disabled) return;
  event.preventDefault();
  const observer = new MutationObserver(() => {
    if (!trigger.isConnected) {
      observer.disconnect();
      return;
    }
    if (!trigger.disabled) {
      observer.disconnect();
      trigger.focus();
    }
  });
  observer.observe(trigger, {
    attributes: true,
    attributeFilter: ["disabled"],
  });
  // 성공해 trigger 가 DOM 에서 빠지면 속성 변화가 더는 오지 않는다 — 감시를 오래 남기지 않는다.
  window.setTimeout(() => observer.disconnect(), FOCUS_RETURN_TIMEOUT_MS);
}
