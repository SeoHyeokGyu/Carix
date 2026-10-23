import { type ReactNode, useEffect, useRef, useState } from "react";
import { flushSync } from "react-dom";
import {
  redirect,
  useLoaderData,
  useNavigate,
  useSearchParams,
} from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";
import { InlineAlert } from "@/components/ui/inline-alert";
import { cn } from "@/lib/utils";

import { CurrentJobStep } from "../../components/simulator/CurrentJobStep";
import { MoveConditionsStep } from "../../components/simulator/MoveConditionsStep";
import { OverrideNotice } from "../../components/simulator/OverrideNotice";
import { ResultView } from "../../components/simulator/ResultView";
import { SavedScenarioMenu } from "../../components/simulator/SavedScenarioMenu";
import { SaveScenarioForm } from "../../components/simulator/SaveScenarioForm";
import { SimulatorSteps } from "../../components/simulator/SimulatorSteps";
import {
  EvaluationFallback,
  StayResultStep,
} from "../../components/simulator/StayResultStep";
import { StepNavBar } from "../../components/simulator/StepNavBar";
import { StepSection } from "../../components/simulator/StepSection";
import type {
  EmitSource,
  StepFormHandle,
} from "../../components/simulator/useDebouncedEmit";
import { useEvaluation } from "../../components/simulator/useEvaluation";
import { useUndoableAdopt } from "../../components/simulator/useUndoableAdopt";
import { listIncome } from "../../lib/api/income";
import {
  type EvaluateRequest,
  getContext,
  getDraft,
  listScenarios,
  putDraft,
  type SavedScenario,
  saveScenario,
  type ScenarioContext,
} from "../../lib/api/scenario";
import { UnauthorizedError } from "../../lib/coreApi";
import { createDraftQueue } from "../../lib/draftQueue";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { type FillTarget, fillTargetOf } from "../../lib/simulatorFocus";
import { describeError } from "../../lib/simulatorForm";
import {
  initialStep,
  type SimulatorStep,
  stepStatus,
} from "../../lib/simulatorStep";

type Stay = EvaluateRequest["stay"];
type Move = NonNullable<EvaluateRequest["move"]>;

/** ① 결과에는 지금 회사 쪽 미반영만 보인다. 새 회사 항목은 ② 에서 묻는다. */
const CURRENT_UNREFLECTED: ReadonlySet<string> = new Set([
  "PERFORMANCE_BONUS",
  "STOCK",
  "HIRE_DATE",
]);

export function meta() {
  return [
    { title: "이직 시뮬레이터 | Carix" },
    {
      name: "description",
      content: "남을 때와 옮길 때의 세후 실질 소득을 비교합니다",
    },
  ];
}

/** 이 라우트가 직접 상태를 들고 있으므로 쿼리(`?step=`)가 바뀌어도 다시 불러오지 않는다. */
export function shouldRevalidate() {
  return false;
}

export async function clientLoader() {
  try {
    const [context, draft, scenarios, income] = await Promise.all([
      getContext(),
      getDraft(),
      listScenarios(),
      listIncome(),
    ]);
    const latestIncomeId =
      [...income].sort((a, b) =>
        b.effectiveDate.localeCompare(a.effectiveDate),
      )[0]?.id ?? null;
    // 초안 없이 다시 온 사용자는 가장 최근 새 방식 저장분으로 들어간다. 목록은 이미 최근 순이다.
    const latestSaved =
      scenarios.find((s) => s.engineVersion === 2 && s.request)?.request ??
      null;
    const adopted =
      draft ?? (latestSaved && withoutStaleOverride(latestSaved, context));
    return { context, draft: adopted, scenarios, latestIncomeId };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

/**
 * 저장분을 초안으로 가져올 때 옛 "이번만 바꾸기" 연봉을 비운다. 소득 이력이 있으면 그 연봉이 기준이고,
 * 없으면 덮어쓴 값이 유일한 연봉 입력이라 남긴다.
 */
function withoutStaleOverride(
  request: EvaluateRequest,
  context: ScenarioContext,
): EvaluateRequest {
  if (context.currentAnnualGross === null) return request;
  return { ...request, stay: { ...request.stay, currentGrossOverride: null } };
}

/** null·빈 값과 키 순서를 무시한 비교. 다시 그린 입력칸이 같은 값을 내도 재평가하지 않게 한다. */
function canonical(part: object | null | undefined): string {
  if (!part) return "";
  return JSON.stringify(
    Object.entries(part)
      .filter(([, value]) => value != null)
      .sort(([a], [b]) => a.localeCompare(b)),
  );
}

function sameRequest(a: EvaluateRequest, b: EvaluateRequest): boolean {
  return (
    canonical(a.stay) === canonical(b.stay) &&
    canonical(a.move) === canonical(b.move)
  );
}

interface ResultAreaProps {
  /** 결과를 낼 계산 요청이 아직 없을 때의 안내. 있으면 이것만 보인다. */
  hint: string | null;
  /** 이 단계의 결과가 있으면 그린다. 없으면 대체 표시. */
  children: ReactNode | null;
  invalid: boolean;
  dimmed: boolean;
  pending: boolean;
  error: string | null;
  onRetry: () => void;
}

/** 단계 입력 아래 결과 자리. 다시 계산하는 동안·입력이 무효인 동안 직전 결과를 흐리게 남긴다. */
function ResultArea({
  hint,
  children,
  invalid,
  dimmed,
  pending,
  error,
  onRetry,
}: ResultAreaProps) {
  if (hint !== null) {
    return <p className="text-sm text-muted-foreground">{hint}</p>;
  }
  if (children === null) {
    return (
      <EvaluationFallback error={error} pending={pending} onRetry={onRetry} />
    );
  }
  return (
    <div className="flex flex-col gap-4" aria-busy={dimmed}>
      {invalid ? (
        <p role="status" className="text-sm text-muted-foreground">
          입력을 고치면 다시 계산합니다
        </p>
      ) : (
        pending && (
          <p role="status" className="text-sm text-muted-foreground">
            다시 계산하는 중
          </p>
        )
      )}
      {error && <InlineAlert message={error} onRetry={onRetry} />}
      <div className={cn("transition-opacity", dimmed && "opacity-60")}>
        {children}
      </div>
    </div>
  );
}

export default function DashboardSimulator() {
  const loaded = useLoaderData<typeof clientLoader>();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  const [context, setContext] = useState<ScenarioContext>(loaded.context);
  const [scenarios, setScenarios] = useState<SavedScenario[]>(loaded.scenarios);
  // 계산 요청. 렌더용 상태와, 같은 이벤트 안에서 바로 읽을 ref 를 함께 둔다.
  const [request, setRequest] = useState<EvaluateRequest | null>(loaded.draft);
  const requestRef = useRef(request);
  // 들어오기만 해서는 서버 초안을 쓰지 않는다. 처음 값을 바꾸거나 이동·저장 버튼을 누르면 켠다.
  const persisted = useRef(false);
  const [queueDraft] = useState(() => createDraftQueue(putDraft));
  // 불러오기·되돌리기로 계산 요청이 밖에서 바뀔 때 두 단계 입력칸을 새로 그린다.
  const [version, setVersion] = useState(0);
  const [revision, setRevision] = useState(0);
  const [recordSaving, setRecordSaving] = useState(false);
  // ① 의 "따로 받음" 금액이 저장 전이면 결과는 이전 기록 기준이다.
  const [bonusUnsaved, setBonusUnsaved] = useState(false);
  const [invalid, setInvalid] = useState<Record<SimulatorStep, boolean>>({
    current: false,
    move: false,
  });
  const [focus, setFocus] = useState<FillTarget | undefined>();
  const [saveOpen, setSaveOpen] = useState<SimulatorStep | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const currentForm = useRef<StepFormHandle<Stay>>(null);
  const moveForm = useRef<StepFormHandle<Move>>(null);
  const currentHeading = useRef<HTMLHeadingElement>(null);
  const moveHeading = useRef<HTMLHeadingElement>(null);
  // 이동을 마친 뒤 할 일. 쿼리 반영이 렌더 뒤에 오므로 단계가 실제로 바뀐 다음 처리한다.
  const arrival = useRef<{ step: SimulatorStep; toField: boolean } | null>(
    null,
  );

  // 쿼리가 없으면 처음 들어올 때의 초안으로 단계를 정해 두고, 그 뒤 계산 요청이 바뀌어도 단계가 저절로 바뀌지 않게 한다.
  const [entryStep] = useState(() => initialStep(params, loaded.draft));
  const step = initialStep(
    params.has("step") ? params : new URLSearchParams({ step: entryStep }),
    request,
  );
  // ① 이 무효인 동안은 계산 요청이 직전 값으로 남아도 ② 를 누를 수 없게 보인다(스펙 공통 머리).
  const baseStatus = stepStatus(request);
  const status = {
    ...baseStatus,
    moveReachable: baseStatus.moveReachable && !invalid.current,
  };
  const toLogin = () => navigate(LOGIN_PATH, { replace: true });

  const { evaluation, error, pending, retry } = useEvaluation(
    request !== null,
    request,
    toLogin,
    { revision, paused: recordSaving },
  );

  useEffect(() => {
    const next = arrival.current;
    if (!next || next.step !== step) return;
    arrival.current = null;
    // 넣기로 온 경우는 입력 컴포넌트가 그 칸을 펼치고 포커스한다.
    if (next.toField) return;
    window.scrollTo({ top: 0 });
    (step === "current" ? currentHeading : moveHeading).current?.focus();
  }, [step]);

  const saveDraft = (next: EvaluateRequest) => {
    queueDraft(next).catch((error: unknown) => {
      if (error instanceof UnauthorizedError) return toLogin();
      setNotice(`초안을 저장하지 못했습니다: ${describeError(error)}`);
    });
  };

  const commit = (next: EvaluateRequest | null) => {
    requestRef.current = next;
    setRequest(next);
    setNotice(null);
  };

  /** 이동·저장 버튼을 누른 때부터 초안을 저장한다. 처음이면 지금 계산 요청을 바로 저장한다. */
  const startPersisting = () => {
    if (persisted.current) return;
    persisted.current = true;
    if (requestRef.current) saveDraft(requestRef.current);
  };

  const update = (next: EvaluateRequest, source: EmitSource) => {
    const prev = requestRef.current;
    const wasPersisted = persisted.current;
    if (source === "edit") persisted.current = true;
    const changed = prev === null || !sameRequest(prev, next);
    if (changed) commit(next);
    if (persisted.current && (changed || !wasPersisted)) {
      saveDraft(requestRef.current ?? next);
    }
  };

  const markValid = (which: SimulatorStep) =>
    setInvalid((prev) => (prev[which] ? { ...prev, [which]: false } : prev));
  const markInvalid = (which: SimulatorStep) =>
    setInvalid((prev) => (prev[which] ? prev : { ...prev, [which]: true }));

  const onStayChange = (stay: Stay, source: EmitSource) => {
    markValid("current");
    update({ stay, move: requestRef.current?.move ?? null }, source);
  };

  const onMoveChange = (move: Move, source: EmitSource) => {
    markValid("move");
    const prev = requestRef.current;
    // ② 는 ① 계산 요청이 있어야 열린다. 없으면 담을 곳이 없다.
    if (prev) update({ ...prev, move }, source);
  };

  /** 이동만 한다. 떠나는 단계의 입력 반영은 호출자가 맡는다. */
  const show = (next: SimulatorStep, target?: FillTarget) => {
    setSaveOpen(null);
    setFocus(target);
    if (next === step) return;
    arrival.current = { step: next, toField: target !== undefined };
    setParams({ step: next });
  };

  /**
   * ① 을 떠날 때는 대기 중인 편집을 먼저 반영하고, 무효면 이동하지 않는다(flush 가 첫 오류 칸으로 포커스).
   * ② 에서 ① 로는 늘 갈 수 있다 — 두 입력은 마운트된 채라 ② 의 대기 편집은 숨겨진 뒤에도 반영된다.
   */
  const goTo = (next: SimulatorStep, target?: FillTarget) => {
    if (next === step) return show(next, target);
    if (step === "current") {
      if (!currentForm.current?.flush()) return;
      startPersisting();
    }
    show(next, target);
  };

  const fill = (code: string) => {
    const target = fillTargetOf(code);
    if (!target) return;
    if (target.step !== step) return goTo(target.step, target);
    // 같은 칸을 다시 눌러도 포커스가 가도록 한 번 비웠다가 넣는다.
    flushSync(() => setFocus(undefined));
    setFocus(target);
  };

  const formOf = (which: SimulatorStep) =>
    which === "current" ? currentForm : moveForm;

  /** 이동 줄의 저장 버튼. 입력이 무효면 저장 칸을 열지 않는다. */
  const openSave = (which: SimulatorStep) => {
    if (!formOf(which).current?.flush()) return;
    startPersisting();
    setSaveOpen(which);
  };

  const save = async (name: string | null, which: SimulatorStep) => {
    if (!formOf(which).current?.flush()) return false;
    const current = requestRef.current;
    if (!current) return false;
    const payload =
      which === "current" ? { stay: current.stay, move: null } : current;
    const saved = await saveScenario(name, payload);
    setScenarios((prev) => [saved, ...prev]);
    return true;
  };

  const refreshContext = () => {
    getContext().then(setContext, (error: unknown) => {
      if (error instanceof UnauthorizedError) return toLogin();
      setNotice(
        `성과급 범위를 다시 불러오지 못했습니다: ${describeError(error)}`,
      );
    });
  };

  /** 불러오기·되돌리기. 남아 있던 입력값은 버리고 계산 요청으로 입력칸을 새로 그린다. */
  const replaceRequest = (next: EvaluateRequest | null) => {
    setVersion((v) => v + 1);
    setInvalid({ current: false, move: false });
    setSaveOpen(null);
    commit(next);
    persisted.current = true;
    // NOTE: (cjkang) 초안 삭제 API 가 없어 null 로 되돌릴 때는 서버 초안을 그대로 둔다.
    if (next) saveDraft(next);
  };
  const { adopt, undo, notice: adoptNotice } = useUndoableAdopt(replaceRequest);

  const load = (next: EvaluateRequest, title: string, to: SimulatorStep) => {
    adopt(withoutStaleOverride(next, context), requestRef.current, title);
    show(to);
  };

  const dimmed = pending || recordSaving || bonusUnsaved || invalid[step];
  const result = evaluation.result;
  const override = (
    <OverrideNotice gross={request?.stay.currentGrossOverride} />
  );

  return (
    <PageFrame width="narrow">
      <PageHeader
        title="이직 시뮬레이터"
        description="남을 때와 옮길 때의 세후·실질 소득을 비교합니다."
        actions={
          scenarios.length > 0 ? (
            <SavedScenarioMenu
              scenarios={scenarios}
              onLoad={(next, title) =>
                load(next, title, next.move ? "move" : "current")
              }
              onRecalculate={(next, title) => load(next, title, "current")}
            />
          ) : undefined
        }
      />
      <div data-page-section="" className="flex flex-col gap-6">
        <SimulatorSteps
          current={step}
          status={status}
          onSelect={(next) => goTo(next)}
        />
        {adoptNotice && (
          <InlineAlert
            tone="info"
            message={`「${adoptNotice.name ?? "저장분"}」을 불러왔습니다.`}
            action={{ label: "이전 입력으로 되돌리기", onClick: undo }}
          />
        )}
        {notice && (
          <InlineAlert message={notice} onDismiss={() => setNotice(null)} />
        )}

        <StepSection
          title="지금 회사"
          headingRef={currentHeading}
          hidden={step !== "current"}
        >
          <CurrentJobStep
            key={version}
            ref={currentForm}
            context={context}
            latestIncomeId={loaded.latestIncomeId}
            initial={request}
            focus={
              step === "current" && focus?.step === "current"
                ? focus.field
                : undefined
            }
            onChange={onStayChange}
            onInvalid={() => markInvalid("current")}
            onRecordSaving={setRecordSaving}
            onRecordSaved={() => {
              setRevision((r) => r + 1);
              refreshContext();
            }}
            onBonusRecorded={refreshContext}
            onBonusUnsaved={setBonusUnsaved}
          />
          <ResultArea
            hint={
              request === null
                ? "올해 예상 인상률과 연봉을 넣으면 지금 회사 결과가 나옵니다"
                : null
            }
            invalid={invalid.current}
            dimmed={dimmed}
            pending={pending}
            error={error}
            onRetry={retry}
          >
            {result && request && (
              <>
                {override}
                <StayResultStep
                  result={{
                    ...result,
                    unreflected: result.unreflected.filter((code) =>
                      CURRENT_UNREFLECTED.has(code),
                    ),
                  }}
                  raisePercent={request.stay.raisePercent}
                  onFill={fill}
                />
              </>
            )}
          </ResultArea>
          <StepNavBar
            left={
              <Button
                type="button"
                variant="outline"
                onClick={() => openSave("current")}
              >
                이 결과 저장
              </Button>
            }
            right={
              <Button type="button" onClick={() => goTo("move")}>
                새 회사와 비교하기 →
              </Button>
            }
          />
          {saveOpen === "current" && (
            <SaveScenarioForm
              onSave={(name) => save(name, "current")}
              blocked={pending || recordSaving}
            />
          )}
        </StepSection>

        <StepSection
          title="새 회사"
          headingRef={moveHeading}
          hidden={step !== "move"}
        >
          <MoveConditionsStep
            key={version}
            ref={moveForm}
            initial={request}
            focus={
              step === "move" && focus?.step === "move"
                ? focus.field
                : undefined
            }
            onChange={onMoveChange}
            onInvalid={() => markInvalid("move")}
          />
          <ResultArea
            hint={
              request?.move
                ? null
                : "새 회사 연봉과 실업급여 여부를 넣으면 비교가 나옵니다"
            }
            invalid={invalid.move}
            dimmed={dimmed}
            pending={pending}
            error={error}
            onRetry={retry}
          >
            {result?.move ? (
              <>
                {override}
                <ResultView
                  result={{ ...result, move: result.move }}
                  onFill={fill}
                />
              </>
            ) : null}
          </ResultArea>
          <StepNavBar
            left={
              <Button
                type="button"
                variant="outline"
                onClick={() => goTo("current")}
              >
                ← 지금 회사
              </Button>
            }
            right={
              <Button type="button" onClick={() => openSave("move")}>
                시나리오 저장
              </Button>
            }
          />
          {saveOpen === "move" && (
            <SaveScenarioForm
              onSave={(name) => save(name, "move")}
              blocked={pending || recordSaving}
            />
          )}
        </StepSection>
      </div>
    </PageFrame>
  );
}
