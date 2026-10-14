import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

type PageFrameWidth = "wide" | "narrow" | "admin";

export type PageFrameProps = HTMLAttributes<HTMLElement> & {
  width?: PageFrameWidth;
  as?: "div" | "main" | "section";
  children: ReactNode;
};

const WIDTH: Record<PageFrameWidth, string> = {
  wide: "max-w-5xl py-8 sm:py-10",
  narrow: "max-w-5xl py-8 sm:py-10 [&>*:not([data-page-header])]:max-w-3xl",
  admin: "max-w-7xl py-8",
};

/**
 * 화면 바깥 틀. 폭·좌우 여백이 헤더(`AppShell`·`AdminHeader`)와 같아 제목이 로고 선에서 시작한다.
 * 폼처럼 좁아야 하는 화면은 `narrow` 로 헤더 아래 내용만 3xl 로 왼쪽 정렬한다.
 */
export function PageFrame({
  width = "wide",
  as: Tag = "div",
  className,
  children,
  ...rest
}: PageFrameProps) {
  return (
    <Tag
      {...rest}
      data-page-frame={width}
      className={cn("mx-auto w-full px-6", WIDTH[width], className)}
    >
      {children}
    </Tag>
  );
}
