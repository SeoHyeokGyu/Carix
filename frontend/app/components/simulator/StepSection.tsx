import type { ReactNode, Ref } from "react";

import { HEADING } from "@/components/ui/heading";
import { cn } from "@/lib/utils";

interface StepSectionProps {
  title: string;
  /** 단계가 바뀌면 라우트가 이 제목으로 포커스를 옮긴다. */
  headingRef: Ref<HTMLHeadingElement>;
  /** 지금 단계가 아니면 숨긴다. 입력값을 지키려고 언마운트하지 않는다. */
  hidden: boolean;
  children: ReactNode;
}

/** 한 단계 = 입력이 위, 결과가 바로 아래, 이동 줄이 맨 끝. */
export function StepSection({
  title,
  headingRef,
  hidden,
  children,
}: StepSectionProps) {
  return (
    <section hidden={hidden} className="flex flex-col gap-6">
      <h2
        ref={headingRef}
        tabIndex={-1}
        className={cn(HEADING.section, "outline-none")}
      >
        {title}
      </h2>
      {children}
    </section>
  );
}
