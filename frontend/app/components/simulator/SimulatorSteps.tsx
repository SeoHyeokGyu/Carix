import { Check } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import type { SimulatorStep, StepStatus } from "../../lib/simulatorStep";

const ITEMS: readonly {
  step: SimulatorStep;
  label: string;
  optional: boolean;
}[] = [
  { step: "current", label: "① 지금 회사", optional: false },
  { step: "move", label: "② 새 회사", optional: true },
];

interface SimulatorStepsProps {
  current: SimulatorStep;
  status: StepStatus;
  onSelect: (step: SimulatorStep) => void;
}

/** `① 지금 회사 ─ ② 새 회사 (선택)`. 누르면 그 단계로 간다. ② 는 선택 단계라 점선이다. */
export function SimulatorSteps({
  current,
  status,
  onSelect,
}: SimulatorStepsProps) {
  return (
    <nav aria-label="시뮬레이터 단계">
      <ol className="flex items-center gap-2 text-sm">
        {ITEMS.map((item, i) => {
          const active = item.step === current;
          const done =
            item.step === "current" ? status.currentDone : status.moveDone;
          const reachable = item.step === "current" || status.moveReachable;
          return (
            <li
              key={item.step}
              className="flex flex-1 items-center gap-2 last:flex-none"
            >
              <Button
                type="button"
                variant="ghost"
                size="sm"
                aria-current={active ? "step" : undefined}
                aria-disabled={reachable ? undefined : true}
                onClick={() => {
                  if (reachable && !active) onSelect(item.step);
                }}
                className={cn(
                  "h-auto rounded-full border px-3 py-1 text-sm",
                  item.optional && "border-dashed",
                  active && "border-primary font-medium text-primary",
                  !active && done && "text-carix-text",
                  !active && !done && "text-muted-foreground",
                  reachable
                    ? "cursor-pointer"
                    : "cursor-not-allowed opacity-50 hover:bg-transparent",
                )}
              >
                {done && <Check className="size-3.5" aria-hidden="true" />}
                {item.label}
                {done && <span className="sr-only">(완료)</span>}
                {item.optional && (
                  <span className="text-xs text-muted-foreground">
                    선택 입력
                  </span>
                )}
              </Button>
              {i < ITEMS.length - 1 && (
                <span
                  aria-hidden="true"
                  className={cn(
                    "h-px flex-1 border-t",
                    ITEMS[i + 1].optional && "border-dashed",
                  )}
                />
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
