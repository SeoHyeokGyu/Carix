import { Button } from "@/components/ui/button";

import type { EvaluationResponse } from "../../lib/api/scenario";
import {
  inflationNote,
  severanceNote,
  stayHeadline,
} from "../../lib/simulatorCopy";
import { AssumptionsPanel } from "./AssumptionsPanel";
import { RaiseAxis } from "./RaiseAxis";
import { UnreflectedNotice } from "./UnreflectedNotice";

interface StayResultStepProps {
  result: EvaluationResponse;
  /** 사용자가 입력한 예상 인상률(%). */
  raisePercent: number;
  onFill: (code: string) => void;
  onCompare: () => void;
}

function pct(value: number): string {
  return `${value.toFixed(1)}%`;
}

/** ② 남으면. 이 화면 자체가 완결된 결과다. */
export function StayResultStep({
  result,
  raisePercent,
  onFill,
  onCompare,
}: StayResultStepProps) {
  const { stay, inflation, severance } = result;
  const note = inflationNote(inflation);
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
              {pct(government)} (정부 전망 기준)
            </>
          )}
        </li>
        {stay.marketGapPercent !== null && (
          <li>
            시장 중앙값 대비 {stay.marketGapPercent > 0 ? "+" : ""}
            {pct(stay.marketGapPercent)} (세전 기준)
          </li>
        )}
        {note && <li className="text-muted-foreground">{note}</li>}
        {severance && <li>{severanceNote(severance)}</li>}
      </ul>

      <UnreflectedNotice codes={result.unreflected} onFill={onFill} />

      <AssumptionsPanel assumptions={result.assumptions} />

      <div>
        <Button type="button" variant="secondary" onClick={onCompare}>
          이직과 비교하기
        </Button>
      </div>
    </div>
  );
}
