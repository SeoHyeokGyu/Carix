import { useState } from "react";

import { Badge } from "@/components/ui/badge";

import type {
  EvaluateRequest,
  EvaluationResponse,
  SavedScenario,
} from "../../lib/api/scenario";
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
import { QuickEdit } from "./QuickEdit";
import { SavedScenarioList } from "./SavedScenarioList";
import { SaveScenarioForm } from "./SaveScenarioForm";
import { UnreflectedNotice } from "./UnreflectedNotice";

type MoveInput = NonNullable<EvaluateRequest["move"]>;
type MoveView = NonNullable<EvaluationResponse["move"]>;

interface ResultViewProps {
  draft: EvaluateRequest & { move: MoveInput };
  result: EvaluationResponse & { move: MoveView };
  /** 평가 요청이 진행 중이거나 직전 요청이 실패했을 때 결과 영역에 보일 문장. */
  notice: string | null;
  /** 입력이 바뀌어 결과를 다시 계산하는 중. */
  pending: boolean;
  scenarios: readonly SavedScenario[];
  /** 상단 세 칸을 고쳤을 때. 호출자가 초안 저장과 재평가를 맡는다. */
  onEdit: (next: EvaluateRequest) => void;
  onFullConditions: () => void;
  onFill: (code: string) => void;
  /** 저장 실패는 던진다 — 이 화면이 문장으로 보인다. */
  onSave: (name: string | null) => Promise<void>;
  onLoadScenario: (request: EvaluateRequest) => void;
  onRecalculate: (request: EvaluateRequest) => void;
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
      <SectionTitle>1 · 길게 보면</SectionTitle>
      <CumulativeChart
        stay={move.stayCumulative}
        move={move.moveCumulative}
        crossoverMonth={move.crossoverMonth}
      />
      <p className="text-sm">
        {move.crossoverMonth === null
          ? "비교 기간 4년 동안 이직 누적이 잔류 누적을 넘지 못합니다."
          : `${move.crossoverMonth}개월째부터 이직 누적이 잔류 누적을 넘어섭니다.`}{" "}
        4년 끝의 누적 차이는 {signedMan(end)}입니다.
      </p>
    </section>
  );
}

function FirstYear({ move }: { move: MoveView }) {
  const { expectedRange, stock } = move;
  return (
    <section className="flex flex-col gap-3">
      <SectionTitle>2 · 첫해 증감</SectionTitle>
      <FirstYearWaterfall firstYear={move.firstYear} />
      <p className="text-sm">
        첫해 합계 {signedMan(move.firstYear.total)} — 연봉 차이{" "}
        {signedMan(move.firstYear.salaryDiff)}, 공백{" "}
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
          주식(합계에 넣지 않음): 잔류 12개월 베스팅{" "}
          {signedMan(stock.stayVesting12m ?? 0)}, 이직 12개월 베스팅{" "}
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
  // 개인물가를 못 구하면 정부 전망 기준 하나만 보인다
  const breakeven = personal ?? result.stay.breakevenGovernmentPercent;
  return (
    <section className="flex flex-col gap-2">
      <SectionTitle>3 · 남는다면</SectionTitle>
      <ul className="flex flex-col gap-1.5 text-sm">
        {breakeven !== null && (
          <li>
            물가를 이기는 최소 인상률 {pct(breakeven)} (
            {personal !== null ? "개인물가 기준" : "정부 전망 기준"})
          </li>
        )}
        {move.equalizingRaisePercent !== null && (
          <li>
            이직과 4년 누적이 같아지는 인상률 {pct(move.equalizingRaisePercent)}
          </li>
        )}
      </ul>
      <InflationBasisNote inflation={result.inflation} stay={result.stay} />
    </section>
  );
}

export function ResultView(props: ResultViewProps) {
  const { draft, result, notice, pending } = props;
  const [editDirty, setEditDirty] = useState(false);
  const { move } = result;
  const { headline, note } = moveHeadline(
    move.firstYear.total,
    move.crossoverMonth,
  );
  const advice = moveAdvice(move.crossoverMonth);
  const feasibility = move.feasibility;

  return (
    <div className="flex flex-col gap-8">
      <QuickEdit
        draft={draft}
        onEdit={props.onEdit}
        onFullConditions={props.onFullConditions}
        onDirtyChange={setEditDirty}
      />

      {pending && (
        <p role="status" className="text-sm text-muted-foreground">
          다시 계산하는 중
        </p>
      )}

      {notice && (
        <p role="alert" className="text-sm text-carix-danger">
          {notice}
        </p>
      )}

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

      <UnreflectedNotice codes={result.unreflected} onFill={props.onFill} />

      <SaveScenarioForm onSave={props.onSave} blocked={editDirty || pending} />

      <SavedScenarioList
        scenarios={props.scenarios}
        onLoad={props.onLoadScenario}
        onRecalculate={props.onRecalculate}
      />

      <AssumptionsPanel assumptions={result.assumptions} />
    </div>
  );
}
