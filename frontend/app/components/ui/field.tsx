import type { ReactNode } from "react";

import { Label } from "@/components/ui/label";

export interface FieldAria {
  id: string;
  "aria-invalid": boolean;
  "aria-describedby": string | undefined;
}

export interface FieldProps {
  id: string;
  label: string;
  /**
   * 필드 옆에 남기는 설명(왜 이 값이 필요한지, 어떤 형식인지 등). 오류와
   * 무관하게 항상 보여준다 — 오류가 나면 오류 문단이 이걸 밀어내는 게 아니라
   * 나란히 추가된다.
   */
  hint?: string;
  error?: string;
  children: (aria: FieldAria) => ReactNode;
}

/**
 * 라벨 + 입력 슬롯 + 힌트/오류 문단을 한 단위로 묶는 공통 폼 필드.
 * `aria-describedby` 로 입력과 힌트·오류 문단을 연결하여 접근성을 보장한다.
 */
export function Field({ id, label, hint, error, children }: FieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy =
    [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      {children({
        id,
        "aria-invalid": Boolean(error),
        "aria-describedby": describedBy,
      })}
      {hint && (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-sm text-carix-danger">
          {error}
        </p>
      )}
    </div>
  );
}
