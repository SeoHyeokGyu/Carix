import type { EvaluationResponse } from "../../lib/api/scenario";
import {
  marketGapLine,
  severanceNote,
  stayHeadline,
} from "../../lib/simulatorCopy";
import { RaiseAxis } from "./RaiseAxis";

interface StayOutcomeProps {
  result: EvaluationResponse;
  /** 사용자가 입력한 예상 인상률(%). */
  raisePercent: number;
}

/** 지금 회사 카드 안의 남을 때 결과. */
export function StayOutcome({ result, raisePercent }: StayOutcomeProps) {
  const { stay, severance } = result;
  const personal = stay.breakevenPersonalPercent;
  const government = stay.breakevenGovernmentPercent;

  return (
    <div className="flex flex-col gap-6">
      <h2 className="text-2xl font-semibold">
        {stayHeadline(stay.realChangePercent)}
      </h2>

      <RaiseAxis
        expectedPercent={raisePercent}
        breakevenPersonal={personal}
        breakevenGovernment={government}
      />

      {(stay.marketGapPercent !== null || severance) && (
        <ul className="flex flex-col gap-1.5 text-sm">
          {stay.marketGapPercent !== null && (
            <li>{marketGapLine(stay.marketGapPercent)}</li>
          )}
          {severance && <li>{severanceNote(severance)}</li>}
        </ul>
      )}
    </div>
  );
}
