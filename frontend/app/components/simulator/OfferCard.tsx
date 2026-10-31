import { type ReactNode, useEffect, useId, useRef } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";

import {
  SCENARIO_TEMPLATES,
  type ScenarioTemplate,
} from "../../lib/simulatorTemplates";

interface OfferCardProps {
  open: boolean;
  onOpen: () => void;
  onClear: () => void;
  onSelectTemplate?: (template: ScenarioTemplate) => void;
  /** 오퍼 입력. 열려 있을 때만 마운트한다 — 닫으면 입력값을 버린다. */
  children: ReactNode;
}

/** 받은 오퍼가 없으면 점선 안내 카드, 있으면 `새 회사 오퍼` 입력 카드. */
export function OfferCard({
  open,
  onOpen,
  onClear,
  onSelectTemplate,
  children,
}: OfferCardProps) {
  const headingId = useId();
  const sectionRef = useRef<HTMLElement>(null);
  const openButtonRef = useRef<HTMLButtonElement>(null);
  // 누른 버튼이 사라지면 포커스가 body 로 빠진다. 이 카드의 두 버튼으로 열고 닫았을 때만 옮긴다 —
  // 불러오기·되돌리기는 라우트가 포커스를 맡는다.
  const moveFocus = useRef(false);
  useEffect(() => {
    if (!moveFocus.current) return;
    moveFocus.current = false;
    if (open) {
      sectionRef.current?.querySelector<HTMLElement>("#gross")?.focus();
    } else {
      openButtonRef.current?.focus();
    }
  }, [open]);

  const press = (handler: () => void) => () => {
    moveFocus.current = true;
    handler();
  };

  if (!open) {
    return (
      <section ref={sectionRef} aria-labelledby={headingId}>
        <Card className="items-start border-dashed bg-transparent shadow-none gap-4">
          <div>
            <CardTitle as="h2" id={headingId}>
              받은 오퍼가 있나요?
            </CardTitle>
            <p className="text-sm text-muted-foreground mt-1">
              새 회사 연봉을 넣으면 지금 회사와 나란히 비교합니다. 없으면 왼쪽
              결과만으로 충분합니다.
            </p>
          </div>
          <div className="flex flex-col sm:flex-row flex-wrap items-start sm:items-center gap-3">
            <Button
              ref={openButtonRef}
              type="button"
              className="min-h-11"
              onClick={press(onOpen)}
            >
              오퍼 넣기
            </Button>
            {onSelectTemplate && (
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="text-xs text-muted-foreground mr-1">
                  또는 템플릿:
                </span>
                {SCENARIO_TEMPLATES.map((tmpl) => (
                  <Button
                    key={tmpl.id}
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      onOpen();
                      onSelectTemplate(tmpl);
                    }}
                    className="h-8 text-xs"
                  >
                    {tmpl.name}
                  </Button>
                ))}
              </div>
            )}
          </div>
        </Card>
      </section>
    );
  }

  return (
    <section ref={sectionRef} aria-labelledby={headingId}>
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
            onClick={press(onClear)}
          >
            지우기
          </Button>
        </div>
        {children}
      </Card>
    </section>
  );
}
