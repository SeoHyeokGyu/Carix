import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * B 시각 언어의 표. 틀(`framed`)이 기본이고, 이미 `Card` 안에 놓인 표는 `plain` 으로
 * 틀을 벗는다 — `EmptyState` 의 `variant="plain"` 과 같은 규약이다.
 *
 * 가로 스크롤 래퍼는 두 variant 모두 유지한다. 없으면 열이 많은 표가 좁은 폭에서
 * 한글을 글자 단위로 쪼갠다(`S-12` 소득 탭, 375px 에서 "변/경/사/유").
 */
const FRAME_CLASS = {
  framed:
    "overflow-hidden rounded-xl border border-carix-border bg-carix-surface shadow-xs",
  plain: "",
} as const;

function Table({
  className,
  variant = "framed",
  ...props
}: React.ComponentProps<"table"> & { variant?: keyof typeof FRAME_CLASS }) {
  return (
    <div data-slot="table-container" className={FRAME_CLASS[variant]}>
      <div data-slot="table-scroll" className="relative w-full overflow-x-auto">
        <table
          data-slot="table"
          className={cn(
            "w-full caption-bottom text-left text-sm text-carix-text",
            className,
          )}
          {...props}
        />
      </div>
    </div>
  );
}

function TableHeader({ className, ...props }: React.ComponentProps<"thead">) {
  return (
    <thead
      data-slot="table-header"
      className={cn(
        "border-b border-carix-border bg-carix-bg/60 text-xs font-medium text-carix-text/70",
        className,
      )}
      {...props}
    />
  );
}

function TableBody({ className, ...props }: React.ComponentProps<"tbody">) {
  return (
    <tbody
      data-slot="table-body"
      className={cn("[&_tr:last-child]:border-0", className)}
      {...props}
    />
  );
}

function TableFooter({ className, ...props }: React.ComponentProps<"tfoot">) {
  return (
    <tfoot
      data-slot="table-footer"
      className={cn(
        "border-t border-carix-border bg-carix-bg/60 font-medium [&>tr]:last:border-b-0",
        className,
      )}
      {...props}
    />
  );
}

function TableRow({ className, ...props }: React.ComponentProps<"tr">) {
  return (
    <tr
      data-slot="table-row"
      className={cn("border-b border-carix-border/60", className)}
      {...props}
    />
  );
}

function TableHead({ className, ...props }: React.ComponentProps<"th">) {
  return (
    <th
      data-slot="table-head"
      className={cn("px-4 py-3 align-middle font-medium", className)}
      {...props}
    />
  );
}

function TableCell({ className, ...props }: React.ComponentProps<"td">) {
  return (
    <td
      data-slot="table-cell"
      className={cn("px-4 py-3 align-middle", className)}
      {...props}
    />
  );
}

function TableCaption({
  className,
  ...props
}: React.ComponentProps<"caption">) {
  return (
    <caption
      data-slot="table-caption"
      className={cn("mt-4 text-sm text-carix-text/60", className)}
      {...props}
    />
  );
}

export {
  Table,
  TableHeader,
  TableBody,
  TableFooter,
  TableHead,
  TableRow,
  TableCell,
  TableCaption,
};
