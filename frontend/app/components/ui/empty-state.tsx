import type { ComponentPropsWithoutRef, ReactNode } from "react";
import { cn } from "@/lib/utils";
import { ToneBadge, type Tone } from "./tone-badge";

export interface EmptyStateProps extends ComponentPropsWithoutRef<"div"> {
  /**
   * `boxed`(기본) — 점선 박스. 화면에 바로 놓일 때.
   * `plain` — 테두리·배경 없음. 이미 카드 안에 놓일 때. 박스 안의 박스를 만들지 않는다.
   */
  variant?: "boxed" | "plain";
  /** 이모지 한 글자. 없으면 아이콘 자리를 아예 비운다. */
  icon?: ReactNode;
  /** 주면 아이콘이 그 톤의 알약이 된다. 없으면 중립 원형이다. */
  tone?: Tone;
  title: string;
  description?: ReactNode;
  /** 제목 태그. 화면의 제목 계층에 맞춘다 — 기본 h4 는 h1 바로 아래에서 단계를 건너뛴다. */
  headingLevel?: "h2" | "h3" | "h4";
  /** 링크·버튼 같은 다음 행동. */
  children?: ReactNode;
  className?: string;
}

/**
 * 빈 상태·열화 상태의 표준형.
 *
 * 전에는 같은 뜻을 두 가지로 그렸다 — 점선 박스에 아이콘과 표제를 세운 형태와,
 * 아무 테두리 없이 흐린 문단만 얹은 형태. 후자는 화면에서 "로딩이 덜 끝났나" 로 읽혔다.
 * 박스형으로 모은다.
 *
 * `variant` 는 그 두 형태의 부활이 아니다. 구조(아이콘·표제·설명·행동 슬롯)는 한 벌이고,
 * 바깥 테두리만 놓이는 자리에 따라 달라진다 — 이미 카드 안이면 박스를 한 겹 더 그리지 않는다.
 */
const VARIANT_CLASS = {
  boxed:
    "rounded-xl border border-dashed border-carix-border bg-carix-surface p-12 shadow-xs",
  plain: "py-12",
} as const;

export function EmptyState({
  variant = "boxed",
  icon,
  tone,
  title,
  description,
  headingLevel = "h4",
  children,
  className,
  ...rest
}: EmptyStateProps) {
  const Heading = headingLevel;
  return (
    <div
      // 기본 식별자를 먼저 두고 `...rest` 를 뒤에 편다 — 호출부가 덮어쓸 수 있게.
      // 갤러리처럼 한 화면에 여러 개가 놓이면 Playwright strict 모드가 중복으로 던진다.
      data-testid="empty-state"
      {...rest}
      className={cn("text-center", VARIANT_CLASS[variant], className)}
    >
      {icon &&
        (tone ? (
          <ToneBadge
            tone={tone}
            data-testid="empty-state-icon"
            className="mb-3 rounded-full px-3 py-1"
          >
            {icon}
          </ToneBadge>
        ) : (
          <div
            data-testid="empty-state-icon"
            className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-carix-info/10 text-xl"
          >
            {icon}
          </div>
        ))}
      <Heading className="text-base font-bold text-carix-text">{title}</Heading>
      {description && (
        <p className="mt-2 text-sm text-carix-text/70">{description}</p>
      )}
      {children && <div className="mt-6">{children}</div>}
    </div>
  );
}
