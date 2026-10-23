import { X } from "lucide-react";
import type { ReactNode } from "react";

import { Button } from "./button";

export type InlineAlertProps = {
  message: ReactNode;
  /** 있으면 「다시 시도」. 이전 데이터를 남긴 채 다시 불러올 때. */
  onRetry?: () => void;
  retrying?: boolean;
  /** 있으면 닫기. 동작 실패·검증 문장처럼 사용자가 읽고 치울 알림. */
  onDismiss?: () => void;
  /** 기본은 실패(`danger`). `info` 는 되돌리기 안내처럼 실패가 아닌 알림 — 색을 쓰지 않고 status 로 읽힌다. */
  tone?: "danger" | "info";
  /** 「다시 시도」 가 아닌 동작 버튼 하나. */
  action?: { label: string; onClick: () => void };
};

/**
 * 내용을 남긴 채 알리는 한 줄 실패(`tone="info"` 면 실패가 아닌 한 줄 안내). 보여 줄 데이터가 없는 실패는 이것이 아니라
 * `EmptyState tone="danger"` 다 — 화면을 비우지 않을 때만 쓴다.
 */
export function InlineAlert({
  message,
  onRetry,
  retrying = false,
  onDismiss,
  tone = "danger",
  action,
}: InlineAlertProps) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className="flex flex-wrap items-center gap-3"
    >
      <p
        className={
          tone === "danger"
            ? "text-sm text-carix-danger"
            : "text-sm text-carix-text"
        }
      >
        {message}
      </p>
      {action && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={action.onClick}
        >
          {action.label}
        </Button>
      )}
      {onRetry && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={retrying}
          onClick={onRetry}
        >
          다시 시도
        </Button>
      )}
      {onDismiss && (
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          aria-label="알림 닫기"
          onClick={onDismiss}
        >
          <X />
        </Button>
      )}
    </div>
  );
}
