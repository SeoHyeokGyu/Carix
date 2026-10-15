import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * B 시각 언어의 표. 틀(`framed`)이 기본이고, 이미 `Card` 안에 놓인 표는 `plain` 으로
 * 틀을 벗는다 — `EmptyState` 의 `variant="plain"` 과 같은 규약이다.
 *
 * 좁은 폭(`S-12` 소득 탭, 375px)에서 한글이 "변/경/사/유" 로 쌓이지 않게 하는 것은 칸의
 * `break-keep` 이다 — 표가 폭에 안 들어가면 열이 최소 폭으로 줄고, 한글은 음절 사이 어디서나
 * 끊긴다. 가로 스크롤 래퍼는 그렇게 넓어진 표를 받아내는 쪽이라 두 variant 모두 유지한다.
 * `whitespace-nowrap` 은 쓰지 않는다 — 여러 줄이 필요한 칸(사유·뱃지 묶음)까지 한 줄로 묶어
 * 표를 크게 넓힌다.
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
      className={cn("px-4 py-3 align-middle font-medium break-keep", className)}
      {...props}
    />
  );
}

function TableCell({ className, ...props }: React.ComponentProps<"td">) {
  return (
    <td
      data-slot="table-cell"
      className={cn("px-4 py-3 align-middle break-keep", className)}
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
  TableBody,
  TableCaption,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
};
