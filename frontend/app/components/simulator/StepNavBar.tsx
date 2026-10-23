import type { ReactNode } from "react";

/** 단계 하단 이동 줄. 왼쪽은 보조 동작, 오른쪽은 주 버튼. */
export function StepNavBar({
  left,
  right,
}: {
  left?: ReactNode;
  right: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-carix-border pt-4">
      <div>{left}</div>
      <div>{right}</div>
    </div>
  );
}
