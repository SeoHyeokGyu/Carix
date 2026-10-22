import { Check } from "lucide-react";

import { cn } from "@/lib/utils";

import type { SimulatorStep } from "../../lib/simulatorStep";

const ITEMS: readonly {
  step: Exclude<SimulatorStep, "result">;
  label: string;
  optional: boolean;
}[] = [
  { step: "current", label: "현 직장", optional: false },
  { step: "stay", label: "남으면", optional: false },
  { step: "move", label: "이직 조건", optional: true },
];

function currentIndex(current: SimulatorStep): number {
  // 결과 화면은 세 단계를 모두 지나온 상태다.
  return current === "result"
    ? ITEMS.length
    : ITEMS.findIndex((item) => item.step === current);
}

/** `현 직장 ─ 남으면 ─ 이직 조건`. 이직 조건은 선택 단계라 점선이다. */
export function SimulatorSteps({ current }: { current: SimulatorStep }) {
  const index = currentIndex(current);
  return (
    <nav aria-label="시뮬레이터 단계">
      <ol className="flex items-center gap-2 text-sm">
        {ITEMS.map((item, i) => {
          const done = i < index;
          const active = i === index;
          return (
            <li
              key={item.step}
              aria-current={active ? "step" : undefined}
              className="flex flex-1 items-center gap-2 last:flex-none"
            >
              <span
                className={cn(
                  "flex items-center gap-1.5 rounded-full border px-3 py-1",
                  item.optional && "border-dashed",
                  active && "border-primary font-medium text-primary",
                  done && "text-carix-text",
                  !active && !done && "text-muted-foreground",
                )}
              >
                {done && <Check className="size-3.5" aria-hidden="true" />}
                {item.label}
                {done && <span className="sr-only">(완료)</span>}
                {item.optional && (
                  <span className="text-xs text-muted-foreground">선택</span>
                )}
              </span>
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
