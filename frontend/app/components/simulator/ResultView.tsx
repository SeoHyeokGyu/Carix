import { Badge } from "@/components/ui/badge";

import type { EvaluationResponse } from "../../lib/api/scenario";
import {
  moveAdvice,
  moveHeadline,
  severanceNote,
  signedMan,
} from "../../lib/simulatorCopy";
import { AssumptionsPanel } from "./AssumptionsPanel";
import { CumulativeChart } from "./CumulativeChart";
import { FirstYearWaterfall } from "./FirstYearWaterfall";
import { InflationBasisNote } from "./InflationBasisNote";
import { UnreflectedNotice } from "./UnreflectedNotice";

type MoveView = NonNullable<EvaluationResponse["move"]>;

interface ResultViewProps {
  result: EvaluationResponse & { move: MoveView };
  onFill: (code: string) => void;
}

function pct(value: number): string {
  return `${value.toFixed(1)}%`;
}

function SectionTitle({ children }: { children: string }) {
  return <h3 className="text-base font-semibold">{children}</h3>;
}

function LongRun({ move }: { move: MoveView }) {
  const last = Math.min(move.stayCumulative.length, move.moveCumulative.length);
  const end =
    last > 0
      ? move.moveCumulative[last - 1] - move.stayCumulative[last - 1]
      : 0;
  return (
    <section className="flex flex-col gap-3">
      <SectionTitle>1 · 4년 누적</SectionTitle>
      <CumulativeChart
        stay={move.stayCumulative}
        move={move.moveCumulative}
        crossoverMonth={move.crossoverMonth}
      />
      <p className="text-sm">
        {move.crossoverMonth === null
          ? "4년 안에 새 회사 누적이 지금 회사 누적을 따라잡지 못합니다."
          : `${move.crossoverMonth}개월째 새 회사 누적이 지금 회사 누적을 따라잡습니다.`}{" "}
        4년 끝의 누적 차이는 {signedMan(end)}입니다.
      </p>
    </section>
  );
}

function FirstYear({ move }: { move: MoveView }) {
  const { expectedRange, stock } = move;
  return (
    <section className="flex flex-col gap-3">
      <SectionTitle>2 · 첫해 내역</SectionTitle>
      <FirstYearWaterfall firstYear={move.firstYear} />
      <p className="text-sm">
        첫해 합계 {signedMan(move.firstYear.total)} — 연봉 차이{" "}
        {signedMan(move.firstYear.salaryDiff)}, 쉬는 기간 소득{" "}
        {signedMan(move.firstYear.gap)}, 일회성 비용{" "}
        {signedMan(move.firstYear.oneTimeCost)}
        {move.firstYear.benefit !== 0 &&
          `, 실업급여 ${signedMan(move.firstYear.benefit)}`}
        .
      </p>
      {(expectedRange.min !== 0 || expectedRange.max !== 0) && (
        <p className="text-sm text-muted-foreground">
          성과급·사이닝 같은 기대 보상은 연 {signedMan(expectedRange.min)} ~{" "}
          {signedMan(expectedRange.max)} 범위로 더 받을 수 있지만 합계에는 넣지
          않았습니다.
        </p>
      )}
      {stock && (
        <p className="text-sm text-muted-foreground">
          1년 안에 받을 주식(합계에 넣지 않음): 지금 회사{" "}
          {signedMan(stock.stayVesting12m ?? 0)}, 새 회사{" "}
          {signedMan(stock.moveVesting12m ?? 0)}
        </p>
      )}
      {stock && (stock.forfeitedUnvested ?? 0) > 0 && (
        <p role="note" className="text-sm font-medium text-carix-danger">
          이직하면 아직 받지 못한 주식 {signedMan(stock.forfeitedUnvested ?? 0)}
          을 포기합니다.
        </p>
      )}
    </section>
  );
}

function IfYouStay({
  result,
  move,
}: {
  result: EvaluationResponse;
  move: MoveView;
}) {
  const personal = result.stay.breakevenPersonalPercent;
  // 개인물가를 못 구하면 한국은행 전망 기준 하나만 보인다
  const breakeven = personal ?? result.stay.breakevenGovernmentPercent;
  return (
    <section className="flex flex-col gap-2">
      <SectionTitle>3 · 남을 때</SectionTitle>
      <ul className="flex flex-col gap-1.5 text-sm">
        {breakeven !== null && (
          <li>
            물가를 이기는 최소 인상률 {pct(breakeven)} (
            {personal !== null ? "개인물가 기준" : "한국은행 전망 기준"})
          </li>
        )}
        {move.equalizingRaisePercent !== null && (
          <li>
            새 회사와 4년 누적이 같아지는 인상률{" "}
            {pct(move.equalizingRaisePercent)}
          </li>
        )}
      </ul>
      <InflationBasisNote inflation={result.inflation} stay={result.stay} />
    </section>
  );
}

/** ② 새 회사 입력 아래의 비교 결과. */
export function ResultView({ result, onFill }: ResultViewProps) {
  const { move } = result;
  const { headline, note } = moveHeadline(
    move.firstYear.total,
    move.crossoverMonth,
  );
  const advice = moveAdvice(move.crossoverMonth);
  const feasibility = move.feasibility;

  return (
    <div className="flex flex-col gap-8">
      <section aria-live="polite" className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="text-2xl font-semibold">{headline}</h2>
          {result.typeLabel && <Badge>{result.typeLabel}</Badge>}
        </div>
        {note && <p className="text-sm">{note}</p>}
        {advice && <p className="text-sm font-medium">{advice}</p>}
        {feasibility.status === "OK" && feasibility.feedbackMessage && (
          <p className="text-sm text-muted-foreground">
            {feasibility.feedbackMessage}
          </p>
        )}
      </section>

      <LongRun move={move} />
      <FirstYear move={move} />
      <IfYouStay result={result} move={move} />

      {result.severance && (
        <p className="text-sm font-medium">{severanceNote(result.severance)}</p>
      )}

      <UnreflectedNotice codes={result.unreflected} onFill={onFill} />

      <AssumptionsPanel assumptions={result.assumptions} />
    </div>
  );
}
