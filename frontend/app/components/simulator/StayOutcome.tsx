import type { EvaluationResponse } from "../../lib/api/scenario";
import {
  marketGapLine,
  severanceNote,
  stayHeadline,
} from "../../lib/simulatorCopy";
import { RaiseAxis } from "./RaiseAxis";
import { UnreflectedNotice } from "./UnreflectedNotice";

/** 지금 회사 카드 안에는 지금 회사 쪽 미반영만 보인다. 새 회사 항목은 오퍼 카드 쪽 소관이다. */
const CURRENT_UNREFLECTED: ReadonlySet<string> = new Set([
  "PERFORMANCE_BONUS",
  "STOCK",
  "HIRE_DATE",
]);

interface StayOutcomeProps {
  result: EvaluationResponse;
  /** 사용자가 입력한 예상 인상률(%). */
  raisePercent: number;
  onFill: (code: string) => void;
  /** 결과가 바뀔 때 스크린리더에 알릴지. `aria-live` 는 화면에서 한 곳만 켠다. */
  live: boolean;
}

function pct(value: number): string {
  return `${value.toFixed(1)}%`;
}

/** 지금 회사 카드 안의 남을 때 결과. */
export function StayOutcome({
  result,
  raisePercent,
  onFill,
  live,
}: StayOutcomeProps) {
  const { stay, severance } = result;
  const personal = stay.breakevenPersonalPercent;
  const government = stay.breakevenGovernmentPercent;

  return (
    <div className="flex flex-col gap-6">
      <h2
        className="text-2xl font-semibold"
        aria-live={live ? "polite" : undefined}
      >
        {stayHeadline(stay.realChangePercent)}
      </h2>

      <RaiseAxis
        expectedPercent={raisePercent}
        breakevenPersonal={personal}
        breakevenGovernment={government}
      />

      <ul className="flex flex-col gap-1.5 text-sm">
        <li>
          예상 인상률 {pct(raisePercent)}
          {personal !== null && (
            <> · 물가를 이기는 최소 인상률 {pct(personal)} (개인물가 기준)</>
          )}
          {government !== null && (
            <>
              {personal !== null ? ", " : " · 물가를 이기는 최소 인상률 "}
              {pct(government)} (한국은행 전망 기준)
            </>
          )}
        </li>
        {stay.marketGapPercent !== null && (
          <li>{marketGapLine(stay.marketGapPercent)}</li>
        )}
        {severance && <li>{severanceNote(severance)}</li>}
      </ul>

      <UnreflectedNotice
        codes={result.unreflected.filter((code) =>
          CURRENT_UNREFLECTED.has(code),
        )}
        onFill={onFill}
      />
    </div>
  );
}
