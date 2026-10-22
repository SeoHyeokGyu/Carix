import { Button } from "@/components/ui/button";

import { unreflectedLabel } from "../../lib/simulatorCopy";

interface UnreflectedNoticeProps {
  codes: readonly string[];
  /** 항목별 `넣기`. 해당 입력칸으로 보내는 일은 호출자가 한다. */
  onFill: (code: string) => void;
}

/** `미반영: 성과급 [넣기] 주식 [넣기]` */
export function UnreflectedNotice({ codes, onFill }: UnreflectedNoticeProps) {
  if (codes.length === 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
      <span className="font-medium">미반영:</span>
      {codes.map((code) => {
        const label = unreflectedLabel(code);
        return (
          <span key={code} className="flex items-center gap-1">
            {label}
            <Button
              type="button"
              variant="outline"
              size="xs"
              aria-label={`${label} 넣기`}
              onClick={() => onFill(code)}
            >
              넣기
            </Button>
          </span>
        );
      })}
    </div>
  );
}
