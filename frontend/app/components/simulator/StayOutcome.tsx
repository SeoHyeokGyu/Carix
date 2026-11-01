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

function pct(value: number): string {
  return `${value.toFixed(1)}%`;
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
    </div>
  );
}
