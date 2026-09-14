import { cn } from "@/lib/utils";

export interface LoadingStateProps {
  message: string;
  className?: string;
}

/**
 * 로딩 박스의 표준형.
 *
 * `EmptyState` 와 테두리를 나눈다 — 빈 상태는 점선, 로딩은 실선이다. 전에는 둘 다
 * 흐린 텍스트 박스라 "비었다" 와 "아직 안 왔다" 가 화면에서 구분되지 않았다.
 */
export function LoadingState({ message, className }: LoadingStateProps) {
  return (
    <div
      role="status"
      aria-busy="true"
      className={cn(
        "rounded-xl border border-carix-border bg-carix-surface p-12 text-center text-sm text-carix-text/60 shadow-xs",
        className,
      )}
    >
      {message}
    </div>
  );
}
