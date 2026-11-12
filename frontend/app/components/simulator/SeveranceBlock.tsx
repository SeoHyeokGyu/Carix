import { useId } from "react";

import { Card, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

import type { SeveranceView } from "../../lib/api/scenario";
import {
  SEVERANCE_DC_NOTE,
  SEVERANCE_NO_HIRE_DATE,
  SEVERANCE_TITLE,
  severanceAssumedDbNote,
  severanceBasisLine,
  severancePointParts,
} from "../../lib/simulatorCopy";

interface SeveranceBlockProps {
  severance: SeveranceView;
  /** 기준 줄 아래에 붙이는 퇴직금 가정 문장(응답의 가정 문구). */
  notes: string[];
  /** 입력이 바뀌어 다시 계산하는 동안 흐리게. */
  dimmed?: boolean;
}

/**
 * 퇴사 시점별 퇴직금 카드. 「남을 때」가 아니라 「나갈 때」 이야기라 지금 회사 카드 밖, 두 카드 아래 전체 폭에 둔다.
 * DC형·입사일 없음이면 금액 대신 안내 한 줄.
 */
export function SeveranceBlock({
  severance,
  notes,
  dimmed = false,
}: SeveranceBlockProps) {
  const headingId = useId();
  const assumedDb = severanceAssumedDbNote(severance);
  return (
    <section aria-labelledby={headingId} aria-busy={dimmed}>
      <Card className={cn("gap-3 transition-opacity", dimmed && "opacity-60")}>
        <CardTitle as="h2" id={headingId}>
          {SEVERANCE_TITLE}
        </CardTitle>
        {severance.status === "DC" && (
          <p className="text-sm">{SEVERANCE_DC_NOTE}</p>
        )}
        {severance.status === "NO_HIRE_DATE" && (
          <p className="text-sm">{SEVERANCE_NO_HIRE_DATE}</p>
        )}
        {severance.status === "OK" && (
          <>
            <ul className="m-0 grid list-none gap-3 p-0 sm:grid-cols-3">
              {severance.points.map((point) => {
                const parts = severancePointParts(point);
                return (
                  <li
                    key={point.kind}
                    className="flex flex-col gap-0.5 rounded-lg border border-carix-border px-4 py-3"
                  >
                    <span className="text-sm text-muted-foreground">
                      {parts.label}
                    </span>
                    <span className="text-xl font-semibold tabular-nums">
                      {parts.amount}
                    </span>
                    {parts.delta && (
                      <span className="text-sm tabular-nums text-muted-foreground">
                        오늘보다 {parts.delta}
                      </span>
                    )}
                  </li>
                );
              })}
            </ul>
            <div className="flex flex-col gap-1 text-xs text-muted-foreground">
              <p>{severanceBasisLine(severance.points)}</p>
              {notes.map((note) => (
                <p key={note}>{note}</p>
              ))}
              {assumedDb && <p>{assumedDb}</p>}
            </div>
          </>
        )}
      </Card>
    </section>
  );
}
