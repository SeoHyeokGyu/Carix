import { ArrowLeft, ArrowRight } from "lucide-react";
import type { ComponentProps } from "react";
import { Link } from "react-router";

import { cn } from "@/lib/utils";

type TextLinkSize = "xs" | "sm" | "inherit";

export type TextLinkProps = ComponentProps<typeof Link> & {
  direction?: "forward" | "back" | "none";
  size?: TextLinkSize;
};

const TEXT_SIZE: Record<TextLinkSize, string> = {
  xs: "text-xs",
  sm: "text-sm",
  inherit: "",
};

const ICON_SIZE: Record<TextLinkSize, string> = {
  xs: "size-3.5",
  sm: "size-4",
  inherit: "size-[1em]",
};

/**
 * 화면 이동 글자 링크. 그 자리에서 실행하는 것은 `Button variant="link"` 다.
 *
 * 화살표 없는 링크(`none`)는 문장 속·표 안에 놓여 색만으로 주변 글자와 갈린다. `carix-info` 와
 * 본문색의 명도 대비가 3:1 에 못 미쳐(WCAG 1.4.1) 늘 밑줄을 긋는다.
 */
export function TextLink({
  direction = "forward",
  size = "xs",
  className,
  children,
  ...rest
}: TextLinkProps) {
  const icon = ICON_SIZE[size];
  return (
    <Link
      {...rest}
      className={cn(
        "-my-1 inline-flex items-center gap-1 rounded-sm py-1 font-medium text-carix-info underline-offset-4 outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
        direction === "none" ? "underline" : "hover:underline",
        TEXT_SIZE[size],
        className,
      )}
    >
      {direction === "back" && <ArrowLeft className={icon} aria-hidden />}
      {children}
      {direction === "forward" && <ArrowRight className={icon} aria-hidden />}
    </Link>
  );
}
