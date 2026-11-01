import { Button } from "@/components/ui/button";

import { unreflectedLabel } from "../../lib/simulatorCopy";

/** 지금 회사 카드 소관 미반영. 새 회사 항목은 오퍼 카드가 보인다. */
export const CURRENT_UNREFLECTED: ReadonlySet<string> = new Set([
  "PERFORMANCE_BONUS",
  "STOCK",
  "HIRE_DATE",
]);

/** 오퍼 카드 소관 미반영. */
export const MOVE_UNREFLECTED: ReadonlySet<string> = new Set([
  "MOVE_PERFORMANCE_BONUS",
  "SIGNING_BONUS",
  "MOVE_STOCK",
]);

interface UnreflectedNoticeProps {
  codes: readonly string[];
  /** 항목별 `넣기`. 해당 입력칸으로 보내는 일은 호출자가 한다. */
  onFill: (code: string) => void;
}

/**
 * `아직 안 넣은 항목 [+ 성과급] [+ 주식]` — 넣을 접힘 칸 바로 위에 둔다. 항목 이름이 곧 버튼이라
 * 이름과 `넣기` 가 따로 떨어져 보이지 않는다.
 */
export function UnreflectedNotice({ codes, onFill }: UnreflectedNoticeProps) {
  if (codes.length === 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm">
      <span className="text-muted-foreground">아직 안 넣은 항목</span>
      {codes.map((code) => {
        const label = unreflectedLabel(code);
        return (
          <Button
            key={code}
            type="button"
            variant="outline"
            // 터치 높이 44px 는 좁은 화면에서만 — 넓은 화면에서 키우면 칩이 세로로 길쭉해진다.
            className="min-h-11 rounded-full px-3 font-normal sm:min-h-8"
            aria-label={`${label} 넣기`}
            onClick={() => onFill(code)}
          >
            + {label}
          </Button>
        );
      })}
    </div>
  );
}
