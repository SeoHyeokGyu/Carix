import type { EvaluationResponse } from "../../lib/api/scenario";
import {
  inflationGapLine,
  inflationNote,
  inflationSource,
  signedPct,
} from "../../lib/simulatorCopy";

interface InflationBasisNoteProps {
  inflation: EvaluationResponse["inflation"];
  stay: EvaluationResponse["stay"];
}

/**
 * 두 물가 기준 표시. 차이가 크면 두 숫자와 원인 카테고리, 두 기준의 실질 변화를 나란히 놓고, 작거나 개인물가가
 * 없으면 한 줄 안내만 둔다. 출처·기준 시점은 늘 보인다.
 */
export function InflationBasisNote({
  inflation,
  stay,
}: InflationBasisNoteProps) {
  const gapLine = inflationGapLine(inflation);
  const note = inflationNote(inflation);
  return (
    <div className="flex flex-col gap-1 text-sm">
      {gapLine && (
        <>
          <p>{gapLine}</p>
          <p>
            실질 소득 변화: 개인물가 기준 {signedPct(stay.realChangePercent)} ·
            정부 전망 기준 {signedPct(stay.realChangeGovernmentPercent)}
          </p>
        </>
      )}
      {!gapLine && note && <p className="text-muted-foreground">{note}</p>}
      <p className="text-xs text-muted-foreground">
        {inflationSource(inflation)}
      </p>
    </div>
  );
}
