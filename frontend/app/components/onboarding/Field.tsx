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
  /**
   * 필드 옆에 남기는 설명(왜 이 값이 필요한지, 어떤 형식인지 등). 오류와
   * 무관하게 항상 보여준다 — 오류가 나면 오류 문단이 이걸 밀어내는 게 아니라
   * 나란히 추가된다. 소득 화면의 "PPI 계산에 필수" 안내처럼 화면마다 다른
   * 필드 설명이 반복될 것을 대비해 뽑았다.
   */
  hint?: string;
  error?: string;
  children: (aria: FieldAria) => ReactNode;
}

/**
 * 라벨 + 입력 슬롯 + 힌트/오류 문단을 한 단위로 묶는다. `aria-describedby` 로
 * 입력과 힌트·오류 문단을 연결하는 배선이 여기 한 곳에만 있으므로, 화면마다
 * 필드를 이 컴포넌트로 그리기만 하면 접근성 배선이 공짜로 따라온다 — 각
 * 화면에서 따로 신경 쓸 필요가 없다.
 *
 * 힌트와 오류가 동시에 있으면 `aria-describedby` 에 두 id 를 공백으로 이어
 * 함께 넣는다 — 스크린 리더가 힌트 하나만 읽거나 오류 하나만 읽고 마는 게
 * 아니라 둘 다 읽어야 하기 때문이다(오류가 힌트를 대체하는 게 아니다).
 *
 * 입력 요소 자체(`<input>`·`<select>` 등)는 `children` 렌더 프롭으로 받는다 —
 * 컨트롤 종류가 화면마다 다르기 때문이다. 렌더 프롭이 받는 `aria` 객체를 실제
 * 컨트롤에 그대로 스프레드하면 `id`·`aria-invalid`·`aria-describedby` 가 전부
 * 맞물린다.
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
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
