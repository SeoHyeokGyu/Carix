import type { SelectHTMLAttributes } from "react";

import { cn } from "@/lib/utils";

export interface SelectOption {
  readonly value: string;
  readonly label: string;
}

export interface NativeSelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  options?: readonly SelectOption[];
  placeholder?: string;
}

/**
 * shadcn 의 Radix 기반 Select 대신 사용하는 네이티브 `<select>` 컴포넌트.
 * 테스팅 라이브러리와 스크린 리더 호환성을 보장하며 일관된 폼 스타일을 제공한다.
 */
export function NativeSelect({
  options,
  placeholder,
  className,
  children,
  ...props
}: NativeSelectProps) {
  return (
    <select
      data-slot="select"
      className={cn(
        "h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm shadow-xs outline-none transition-[color,box-shadow]",
        "focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50",
        "aria-invalid:border-destructive aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40",
        "disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30",
        className,
      )}
      {...props}
    >
      {options ? (
        <>
          {placeholder && <option value="">{placeholder}</option>}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </>
      ) : (
        children
      )}
    </select>
  );
}
