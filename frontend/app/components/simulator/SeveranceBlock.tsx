import type { SeveranceView } from "../../lib/api/scenario";
import {
  SEVERANCE_DC_NOTE,
  SEVERANCE_NO_HIRE_DATE,
  SEVERANCE_TITLE,
  severanceBasisLine,
  severancePointLine,
} from "../../lib/simulatorCopy";

/** 지금 회사 카드의 퇴직금 블록. DC형·입사일 없음이면 금액 대신 안내 한 줄. */
export function SeveranceBlock({ severance }: { severance: SeveranceView }) {
  return (
    <section
      aria-label={SEVERANCE_TITLE}
      className="flex flex-col gap-1.5 border-l-2 border-carix-border pl-3 text-sm"
    >
      <h3 className="font-medium">{SEVERANCE_TITLE}</h3>
      {severance.status === "DC" && <p>{SEVERANCE_DC_NOTE}</p>}
      {severance.status === "NO_HIRE_DATE" && <p>{SEVERANCE_NO_HIRE_DATE}</p>}
      {severance.status === "OK" && (
        <>
          <ul className="m-0 flex list-none flex-wrap gap-x-3 gap-y-1 p-0 tabular-nums">
            {severance.points.map((point) => (
              <li key={point.kind}>{severancePointLine(point)}</li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">
            {severanceBasisLine(severance.points)}
          </p>
        </>
      )}
    </section>
  );
}
