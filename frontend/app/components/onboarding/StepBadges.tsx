import { Badge } from "@/components/ui/badge";

const STEPS = [
  { step: 1, label: "프로필" },
  { step: 2, label: "소득" },
  { step: 3, label: "소비" },
  { step: 4, label: "스킬" },
  { step: 5, label: "검토" },
] as const;

interface StepBadgesProps {
  current: 1 | 2 | 3 | 4 | 5;
}

/** 온보딩 다섯 화면 공통 진행 표시. 각 화면이 자신의 순번을 `current` 로 넘긴다. */
export function StepBadges({ current }: StepBadgesProps) {
  return (
    <ol aria-label="온보딩 진행 단계" className="flex flex-wrap items-center gap-2">
      {STEPS.map(({ step, label }) => (
        <li key={step}>
          <Badge
            variant={
              step === current ? "default" : step < current ? "secondary" : "outline"
            }
            aria-current={step === current ? "step" : undefined}
          >
            {step}. {label}
          </Badge>
        </li>
      ))}
    </ol>
  );
}
