import type { ReactNode, Ref } from "react";

import { TextLink } from "@/components/ui/text-link";
import { cn } from "@/lib/utils";

export type PageHeaderProps = {
  /** 메뉴 이름. 날짜·부연은 description 으로 보낸다. */
  title: ReactNode;
  description?: ReactNode;
  /** 주 메뉴에 없는 하위 화면만. 항상 상위 주소로 간다. */
  back?: { to: string; label: string };
  /** 보조 TextLink 들과 서버 동작 버튼 최대 하나. 다시 불러오기 버튼은 두지 않는다. */
  actions?: ReactNode;
  /** 바로 아래가 탭이면 구분선. */
  divider?: boolean;
  /** 주면 제목이 스크립트 포커스를 받을 수 있다(탭 순서에는 들지 않음). 사라지는 알림에서 포커스를 옮길 곳. */
  titleRef?: Ref<HTMLHeadingElement>;
};

export function PageHeader({
  title,
  description,
  back,
  actions,
  divider = false,
  titleRef,
}: PageHeaderProps) {
  return (
    <header
      data-page-header=""
      className={cn("mb-6", divider && "border-b border-carix-border pb-4")}
    >
      {back && (
        <TextLink to={back.to} direction="back" size="sm" className="mb-3">
          {back.label}
        </TextLink>
      )}
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div>
          <h1
            ref={titleRef}
            tabIndex={titleRef ? -1 : undefined}
            className="font-display text-2xl font-bold text-carix-text sm:text-3xl"
          >
            {title}
          </h1>
          {description && (
            <p className="mt-1 text-sm text-carix-text/70">{description}</p>
          )}
        </div>
        {actions && (
          // 좁은 화면에서 버튼이 여럿이면 한 줄을 넘어 가로 스크롤이 생긴다 — 줄을 바꾼다.
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
            {actions}
          </div>
        )}
      </div>
    </header>
  );
}
