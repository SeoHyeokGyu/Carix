import type { ReactNode, Ref } from "react";

import { Button } from "@/components/ui/button";

export interface OptionalItem<K extends string> {
  key: K;
  label: string;
}

interface OptionalChipsProps<K extends string> {
  items: readonly OptionalItem<K>[];
  register: (to: "chip", key: K) => Ref<HTMLButtonElement>;
  onAdd: (key: K) => void;
}

/** `선택 항목 [+ 성과급] [+ 주식]` — 누르면 그 항목 칸이 이 줄 바로 위에 끼워진다. */
export function OptionalChips<K extends string>({
  items,
  register,
  onAdd,
}: OptionalChipsProps<K>) {
  if (items.length === 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm">
      <span className="text-muted-foreground">선택 항목</span>
      {items.map(({ key, label }) => (
        <Button
          key={key}
          ref={register("chip", key)}
          type="button"
          variant="outline"
          // 터치 높이 44px 는 좁은 화면에서만 — 넓은 화면에서 키우면 칩이 세로로 길쭉해진다.
          className="min-h-11 rounded-full px-3 font-normal sm:min-h-8"
          aria-label={`${label} 추가`}
          onClick={() => onAdd(key)}
        >
          + {label}
        </Button>
      ))}
    </div>
  );
}

interface OptionalGroupProps {
  label: string;
  groupRef: Ref<HTMLElement>;
  /** 없으면 뺄 수 없다(이미 저장이 나간 입사일 등). */
  onRemove?: () => void;
  /** 값을 비우는 항목은 `빼기`, 값 없이 닫기만 하는 항목은 `닫기`. */
  removeText?: string;
  children: ReactNode;
}

/** 칩으로 추가한 항목 칸. 제목 줄 오른쪽 버튼으로 다시 칩으로 돌린다. */
export function OptionalGroup({
  label,
  groupRef,
  onRemove,
  removeText = "빼기",
  children,
}: OptionalGroupProps) {
  return (
    <section
      ref={groupRef}
      className="flex flex-col gap-4 rounded-lg border px-4 py-3"
    >
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-medium">{label}</h3>
        {onRemove && (
          <Button
            type="button"
            variant="link"
            className="min-h-11 px-0 sm:min-h-0"
            aria-label={`${label} ${removeText}`}
            // 누르는 동안 칸에서 포커스를 빼지 않는다. 빠지면 칸의 blur 저장(입사일)이 빼기보다 먼저 나가
            // 지우려던 값을 저장한다. 브라우저마다 버튼 클릭의 포커스 이동이 달라 relatedTarget 으로는 못 막는다.
            onMouseDown={(e) => e.preventDefault()}
            onClick={onRemove}
          >
            {removeText}
          </Button>
        )}
      </div>
      {children}
    </section>
  );
}
