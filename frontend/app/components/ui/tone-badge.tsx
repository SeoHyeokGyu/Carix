import type { ComponentPropsWithoutRef } from "react";
import { cn } from "@/lib/utils";

/**
 * 의미 톤 — 어느 도메인의 어휘든 여기 여섯 중 하나로 떨어진다.
 * `muted` 는 프런트가 모르는 코드가 내려왔을 때의 폴백이다.
 */
export type Tone =
  "positive" | "warning" | "danger" | "info" | "action" | "muted";

/** 톤 → 클래스. 뱃지 색 조합은 앱 전체에서 이 표 하나뿐이다. */
export const TONE_CLASS: Record<Tone, string> = {
  positive:
    "bg-carix-positive/15 text-carix-positive border border-carix-positive/30",
  warning:
    "bg-carix-warning/15 text-carix-warning border border-carix-warning/30",
  danger: "bg-carix-danger/15 text-carix-danger border border-carix-danger/30",
  info: "bg-carix-info/15 text-carix-info border border-carix-info/30",
  action: "bg-carix-action/15 text-carix-action border border-carix-action/30",
  muted: "bg-carix-border/40 text-carix-text/70 border border-carix-border",
};

export interface ToneBadgeProps extends ComponentPropsWithoutRef<"span"> {
  tone: Tone;
}

/**
 * 뱃지 표현 프리미티브. 어떤 어휘도 모른다 — 톤과 내용만 받는다.
 * 도메인별 문구·톤 결정은 `lib/decisionSignal.ts`, `lib/indexStatus.ts`,
 * `lib/skillDemandStatus.ts`, `lib/jobStatus.ts` 가 각자 맡는다.
 */
export function ToneBadge({
  tone,
  className,
  children,
  ...rest
}: ToneBadgeProps) {
  return (
    <span
      data-tone={tone}
      className={cn(
        // `w-fit shrink-0` 은 기존 `ui/badge.tsx` 가 들고 있던 것이다. 빼면 flex 컨테이너
        // (`flex flex-col`) 안에서 align-items 기본값 stretch 를 받아 뱃지가 가로로 늘어난다.
        "inline-flex w-fit shrink-0 items-center gap-1 rounded px-2 py-0.5 text-xs font-semibold",
        TONE_CLASS[tone],
        className,
      )}
      {...rest}
    >
      {children}
    </span>
  );
}
