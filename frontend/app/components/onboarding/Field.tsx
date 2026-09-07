import type { ReactNode } from "react";
import { Label } from "@/components/ui/label";

interface FieldAria {
  id: string;
  "aria-invalid": boolean;
  "aria-describedby": string | undefined;
}

interface FieldProps {
  id: string;
  label: string;
  error?: string;
  children: (aria: FieldAria) => ReactNode;
}

/**
 * 라벨 + 입력 슬롯 + 오류 문단을 한 단위로 묶는다. `aria-describedby` 로 입력과
 * 오류 문단을 연결하는 배선이 여기 한 곳에만 있으므로, 화면마다 필드를 이
 * 컴포넌트로 그리기만 하면 접근성 배선이 공짜로 따라온다 — 각 화면에서 따로
 * 신경 쓸 필요가 없다.
 *
 * 입력 요소 자체(`<input>`·`<select>` 등)는 `children` 렌더 프롭으로 받는다 —
 * 컨트롤 종류가 화면마다 다르기 때문이다. 렌더 프롭이 받는 `aria` 객체를 실제
 * 컨트롤에 그대로 스프레드하면 `id`·`aria-invalid`·`aria-describedby` 가 전부
 * 맞물린다.
 */
export function Field({ id, label, error, children }: FieldProps) {
  const errorId = `${id}-error`;
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      {children({
        id,
        "aria-invalid": Boolean(error),
        "aria-describedby": error ? errorId : undefined,
      })}
      {error && (
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
