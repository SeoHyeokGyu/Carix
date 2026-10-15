import type { SelectHTMLAttributes } from "react";

import { cn } from "@/lib/utils";

import type { Option } from "../../lib/schemas/options";

const SELECT_CLASS =
  "h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30";

interface NativeSelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  options: readonly Option[];
  placeholder: string;
}

/**
 * shadcn 의 Radix 기반 `Select` 대신 쓰는 네이티브 `<select>`. Radix `Select` 는
 * 버튼 + 포털 리스트박스라 `@testing-library/user-event` 의 `selectOptions` 로
 * 구동할 수 없다 — 온보딩 다섯 화면은 선택지 필드가 있다면 전부 이 컴포넌트로
 * 통일한다(팀 확정 사항, task-7-report.md 참고). 시각 스타일은 shadcn
 * `select-trigger` 클래스와 맞췄다.
 */
export function NativeSelect({
  options,
  placeholder,
  className,
  ...props
}: NativeSelectProps) {
  return (
    <select className={cn(SELECT_CLASS, className)} {...props}>
      <option value="">{placeholder}</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}
