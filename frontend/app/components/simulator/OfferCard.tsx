import { type ReactNode, useId } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";

interface OfferCardProps {
  open: boolean;
  onOpen: () => void;
  onClear: () => void;
  /** 오퍼 입력. 열려 있을 때만 마운트한다 — 닫으면 입력값을 버린다. */
  children: ReactNode;
}

/** 받은 오퍼가 없으면 점선 안내 카드, 있으면 `새 회사 오퍼` 입력 카드. */
export function OfferCard({ open, onOpen, onClear, children }: OfferCardProps) {
  const headingId = useId();

  if (!open) {
    return (
      <section aria-labelledby={headingId}>
        <Card className="items-start border-dashed bg-transparent shadow-none">
          <CardTitle as="h2" id={headingId}>
            받은 오퍼가 있나요?
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            새 회사 연봉을 넣으면 지금 회사와 나란히 비교합니다. 없으면 왼쪽
            결과만으로 충분합니다.
          </p>
          <Button type="button" onClick={onOpen}>
            오퍼 넣기
          </Button>
        </Card>
      </section>
    );
  }

  return (
    <section aria-labelledby={headingId}>
      <Card>
        <div className="flex items-center justify-between gap-3">
          <CardTitle as="h2" id={headingId}>
            새 회사 오퍼
          </CardTitle>
          <Button
            type="button"
            variant="ghost"
            className="min-h-11 px-3"
            aria-label="새 회사 오퍼 지우기"
            onClick={onClear}
          >
            지우기
          </Button>
        </div>
        {children}
      </Card>
    </section>
  );
}
