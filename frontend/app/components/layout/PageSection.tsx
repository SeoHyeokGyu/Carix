import { type ReactNode, useId } from "react";

import { HEADING } from "@/components/ui/heading";
import { cn } from "@/lib/utils";

export type PageSectionProps = {
  title: ReactNode;
  description?: ReactNode;
  /** 섹션 단위 동작 — 제목 줄 오른쪽. */
  actions?: ReactNode;
  /** 탭 패널처럼 이미 이름이 있는 자리 — 제목 단계만 지키고 보이지 않게. */
  titleHidden?: boolean;
  /** 앵커(`#id`)로 바로 오는 섹션. */
  id?: string;
  className?: string;
  children: ReactNode;
};

/** 화면 안 섹션. 제목은 h2 하나, 제목 → 내용 12px. 섹션끼리 간격은 PageFrame 이 준다. */
export function PageSection({
  title,
  description,
  actions,
  titleHidden = false,
  id: anchorId,
  className,
  children,
}: PageSectionProps) {
  const id = useId();
  if (titleHidden) {
    return (
      <div data-page-section="" id={anchorId} className={className}>
        <h2 className="sr-only">{title}</h2>
        {children}
      </div>
    );
  }
  return (
    <section
      data-page-section=""
      id={anchorId}
      aria-labelledby={id}
      className={cn("flex flex-col gap-3", className)}
    >
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div>
          <h2 id={id} className={HEADING.section}>
            {title}
          </h2>
          {description && (
            <p className="mt-1 text-sm text-carix-text/70">{description}</p>
          )}
        </div>
        {actions && <div className="flex items-center gap-4">{actions}</div>}
      </div>
      {children}
    </section>
  );
}
