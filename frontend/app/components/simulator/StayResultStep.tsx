import { Button } from "@/components/ui/button";

import type { EvaluationResponse } from "../../lib/api/scenario";
import {
  marketGapLine,
  severanceNote,
  stayHeadline,
} from "../../lib/simulatorCopy";
import { AssumptionsPanel } from "./AssumptionsPanel";
import { InflationBasisNote } from "./InflationBasisNote";
import { RaiseAxis } from "./RaiseAxis";
import { UnreflectedNotice } from "./UnreflectedNotice";

interface StayResultStepProps {
  result: EvaluationResponse;
  /** 사용자가 입력한 예상 인상률(%). */
  raisePercent: number;
  onFill: (code: string) => void;
}

function pct(value: number): string {
  return `${value.toFixed(1)}%`;
}

interface EvaluationFallbackProps {
  error: string | null;
  pending: boolean;
  onRetry: () => void;
}

/** 아직 보일 결과가 없을 때 결과 자리. 실패면 오류와 다시 시도, 아니면 계산 중. */
export function EvaluationFallback({
  error,
  pending,
  onRetry,
}: EvaluationFallbackProps) {
  if (error) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p role="alert" className="text-sm text-carix-danger">
          {error}
        </p>
        <Button type="button" variant="outline" onClick={onRetry}>
          다시 시도
        </Button>
      </div>
    );
  }
  return (
    <p role="status" className="text-sm text-muted-foreground">
      {pending ? "계산하는 중입니다." : "결과를 불러오는 중입니다."}
    </p>
  );
}

/** ① 지금 회사 입력 아래의 결과. 미반영은 호출자가 지금 회사 항목만 넘긴다. */
export function StayResultStep({
  result,
  raisePercent,
  onFill,
}: StayResultStepProps) {
  const { stay, inflation, severance } = result;
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
          <li>{marketGapLine(stay.marketGapPercent)}</li>
        )}
        {severance && <li>{severanceNote(severance)}</li>}
      </ul>

      <InflationBasisNote inflation={inflation} stay={stay} />

      <UnreflectedNotice codes={result.unreflected} onFill={onFill} />

      <AssumptionsPanel assumptions={result.assumptions} />
    </div>
  );
}
