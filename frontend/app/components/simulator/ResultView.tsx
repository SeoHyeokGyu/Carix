import { useEffect, useRef, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import type {
  EvaluateRequest,
  EvaluationResponse,
  SavedScenario,
} from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import {
  inflationNote,
  moveAdvice,
  moveHeadline,
  severanceNote,
  signedMan,
} from "../../lib/simulatorCopy";
import {
  describeError,
  manInputToWon,
  wonToManInput,
} from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";
import { AssumptionsPanel } from "./AssumptionsPanel";
import { CumulativeChart } from "./CumulativeChart";
import { FirstYearWaterfall } from "./FirstYearWaterfall";
import { ManAmountField } from "./ManAmountField";
import { SavedScenarioList } from "./SavedScenarioList";
import { UnreflectedNotice } from "./UnreflectedNotice";

const EDIT_DEBOUNCE_MS = 400;
const MAX_GAP_MONTHS = 6;

type MoveInput = NonNullable<EvaluateRequest["move"]>;
type MoveView = NonNullable<EvaluationResponse["move"]>;

interface ResultViewProps {
  draft: EvaluateRequest & { move: MoveInput };
  result: EvaluationResponse & { move: MoveView };
  /** 평가 요청이 진행 중이거나 직전 요청이 실패했을 때 결과 영역에 보일 문장. */
  notice: string | null;
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

function parseEdit(
  raise: string,
  gross: string,
  gap: string,
): { errors: FieldErrors; value: [number, number, number] | null } {
  const errors: FieldErrors = {};
  const raiseNumber = Number(raise);
  const grossWon = manInputToWon(gross);
  const gapNumber = Number(gap);
  if (raise.trim() === "" || !Number.isFinite(raiseNumber)) {
    errors.raise = "인상률을 숫자로 입력해 주세요.";
  }
  if (grossWon === null) errors.gross = "이직 연봉을 입력해 주세요.";
  if (
    gap.trim() === "" ||
    !Number.isInteger(gapNumber) ||
    gapNumber < 0 ||
    gapNumber > MAX_GAP_MONTHS
  ) {
    errors.gap = `공백은 0~${MAX_GAP_MONTHS}개월로 입력해 주세요.`;
  }
  if (Object.keys(errors).length > 0) return { errors, value: null };
  return { errors, value: [raiseNumber, grossWon ?? 0, gapNumber] };
}

/** 결과 화면 상단의 핵심 입력 세 칸. 바꾸면 400ms 뒤 호출자에게 알린다. */
function QuickEdit({
  draft,
  onEdit,
  onFullConditions,
}: Pick<ResultViewProps, "draft" | "onEdit" | "onFullConditions">) {
  const [raise, setRaise] = useState(String(draft.stay.raisePercent));
  const [gross, setGross] = useState(wonToManInput(draft.move.grossSalary));
  const [gap, setGap] = useState(String(draft.move.unemploymentMonths));
  const latest = useRef({ draft, onEdit });
  useEffect(() => {
    latest.current = { draft, onEdit };
  });

  const { errors, value } = parseEdit(raise, gross, gap);
  const [nextRaise, nextGross, nextGap] = value ?? [];

  useEffect(() => {
    if (nextRaise === undefined) return;
    const { draft: current, onEdit: edit } = latest.current;
    if (
      nextRaise === current.stay.raisePercent &&
      nextGross === current.move.grossSalary &&
      nextGap === current.move.unemploymentMonths
    ) {
      return;
    }
    const timer = setTimeout(
      () =>
        edit({
          stay: { ...current.stay, raisePercent: nextRaise },
          move: {
            ...current.move,
            grossSalary: nextGross ?? 0,
            unemploymentMonths: nextGap ?? 0,
          },
        }),
      EDIT_DEBOUNCE_MS,
    );
    return () => clearTimeout(timer);
  }, [nextRaise, nextGross, nextGap]);

  return (
    <div className="flex flex-col gap-3 rounded-lg border px-4 py-3">
      <div className="grid gap-3 sm:grid-cols-3">
        <Field id="quickRaise" label="예상 인상률 (%)" error={errors.raise}>
          {(aria) => (
            <Input
              {...aria}
              type="text"
              inputMode="decimal"
              value={raise}
              onChange={(e) => setRaise(e.target.value)}
            />
          )}
        </Field>
        <ManAmountField
          id="quickGross"
          label="이직 연봉 (만원)"
          value={gross}
          onChange={setGross}
          error={errors.gross}
        />
        <Field id="quickGap" label="공백 (개월)" error={errors.gap}>
          {(aria) => (
            <Input
              {...aria}
              type="text"
              inputMode="numeric"
              value={gap}
              onChange={(e) => setGap(e.target.value)}
            />
          )}
        </Field>
      </div>
      <div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onFullConditions}
        >
          전체 조건
        </Button>
      </div>
    </div>
  );
}

function SaveForm({ onSave }: Pick<ResultViewProps, "onSave">) {
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(
    null,
  );

  const submit = async () => {
    setSaving(true);
    setMessage(null);
    try {
      await onSave(name.trim() === "" ? null : name.trim());
      setName("");
      setMessage({ ok: true, text: "시나리오를 저장했습니다." });
    } catch (error) {
      setMessage({ ok: false, text: describeError(error) });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <Field id="scenarioName" label="시나리오 이름 (선택)">
        {(aria) => (
          <Input
            {...aria}
            type="text"
            placeholder="예: A사 오퍼 제안"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        )}
      </Field>
      <div>
        <Button type="button" disabled={saving} onClick={submit}>
          시나리오 저장
        </Button>
      </div>
      {message && (
        <p
          role={message.ok ? "status" : "alert"}
          className={message.ok ? "text-sm" : "text-sm text-carix-danger"}
        >
          {message.text}
        </p>
      )}
    </div>
  );
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
  return (
    <section className="flex flex-col gap-2">
      <SectionTitle>3 · 남는다면</SectionTitle>
      <ul className="flex flex-col gap-1.5 text-sm">
        {personal !== null && (
          <li>물가를 이기는 최소 인상률 {pct(personal)} (개인물가 기준)</li>
        )}
        {move.equalizingRaisePercent !== null && (
          <li>
            이직과 4년 누적이 같아지는 인상률 {pct(move.equalizingRaisePercent)}
          </li>
        )}
      </ul>
    </section>
  );
}

export function ResultView(props: ResultViewProps) {
  const { draft, result, notice } = props;
  const { move } = result;
  const { headline, note } = moveHeadline(
    move.firstYear.total,
    move.crossoverMonth,
  );
  const advice = moveAdvice(move.crossoverMonth);
  const inflation = inflationNote(result.inflation);
  const feasibility = move.feasibility;

  return (
    <div className="flex flex-col gap-8">
      <QuickEdit
        draft={draft}
        onEdit={props.onEdit}
        onFullConditions={props.onFullConditions}
      />

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
        {inflation && (
          <p className="text-sm text-muted-foreground">{inflation}</p>
        )}
        {feasibility.status === "OK" &&
          feasibility.targetPercentile !== null && (
            <p className="text-sm text-muted-foreground">
              이직 연봉은 비슷한 조건 공개연봉의 상위{" "}
              {feasibility.targetPercentile}% 입니다.
              {feasibility.feedbackMessage && ` ${feasibility.feedbackMessage}`}
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

      <SaveForm onSave={props.onSave} />

      <SavedScenarioList
        scenarios={props.scenarios}
        onLoad={props.onLoadScenario}
        onRecalculate={props.onRecalculate}
      />

      <AssumptionsPanel assumptions={result.assumptions} />
    </div>
  );
}
