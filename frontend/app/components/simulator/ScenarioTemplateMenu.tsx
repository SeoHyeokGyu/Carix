import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

import { formatKoreanSalary } from "../../lib/koreanSalary";
import {
  SCENARIO_TEMPLATES,
  type ScenarioTemplate,
} from "../../lib/simulatorTemplates";

interface ScenarioTemplateMenuProps {
  currentSalary?: number | null;
  onSelect: (template: ScenarioTemplate) => void;
  disabled?: boolean;
}

/**
 * 이직 시뮬레이터 상단에 위치하는 시나리오 템플릿(프리셋) 선택 메뉴.
 * 대표적인 이직 보상 조건(연봉 점프, 스타트업, 공백기 등)을 1클릭으로 적용한다.
 */
export function ScenarioTemplateMenu({
  currentSalary,
  onSelect,
  disabled = false,
}: ScenarioTemplateMenuProps) {
  const [open, setOpen] = useState(false);
  const baseSalary =
    currentSalary && currentSalary > 0 ? currentSalary : 50_000_000;

  const handleSelect = (template: ScenarioTemplate) => {
    setOpen(false);
    onSelect(template);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled}
          aria-haspopup="menu"
          aria-expanded={open}
          className="gap-1.5"
        >
          <span>시나리오 템플릿</span>
          <span aria-hidden="true" className="text-[10px]">
            ▾
          </span>
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        className="w-80 p-2 shadow-lg"
        aria-label="시나리오 템플릿 목록"
      >
        <div className="px-2 py-1.5 border-b border-border/50 mb-1">
          <p className="text-xs font-semibold text-foreground">
            기본 시나리오 프리셋
          </p>
          <p className="text-[11px] text-muted-foreground">
            현재 연봉({formatKoreanSalary(baseSalary)})을 기준으로 계산됩니다.
          </p>
        </div>
        <ul className="flex flex-col gap-1.5">
          {SCENARIO_TEMPLATES.map((template) => {
            const move = template.createMove(baseSalary);
            return (
              <li
                key={template.id}
                className="flex flex-col gap-1 rounded-md p-2 hover:bg-accent/40 transition-colors border border-transparent hover:border-border/40"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-medium text-foreground">
                    {template.name}
                  </span>
                  <Badge
                    variant="secondary"
                    className="text-[10px] px-1.5 py-0"
                  >
                    {template.badge}
                  </Badge>
                </div>
                <p className="text-[11px] text-muted-foreground line-clamp-1">
                  {template.description}
                </p>
                <div className="flex items-center justify-between pt-1">
                  <span className="text-[11px] font-medium text-primary">
                    제안 {formatKoreanSalary(move.grossSalary)}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-7 px-2.5 text-xs"
                    onClick={() => handleSelect(template)}
                  >
                    적용
                  </Button>
                </div>
              </li>
            );
          })}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
