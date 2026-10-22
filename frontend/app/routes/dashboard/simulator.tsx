import { useEffect, useRef, useState } from "react";
import {
  redirect,
  useLoaderData,
  useNavigate,
  useSearchParams,
} from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button } from "@/components/ui/button";

import {
  type CurrentJobFocus,
  CurrentJobStep,
  focusTargetOf,
} from "../../components/simulator/CurrentJobStep";
import {
  MoveConditionsStep,
  type MoveFormValues,
} from "../../components/simulator/MoveConditionsStep";
import { ResultView } from "../../components/simulator/ResultView";
import { SimulatorSteps } from "../../components/simulator/SimulatorSteps";
import { StayResultStep } from "../../components/simulator/StayResultStep";
import { listIncome } from "../../lib/api/income";
import {
  evaluate,
  type EvaluateRequest,
  type EvaluationResponse,
  getContext,
  getDraft,
  listScenarios,
  putDraft,
  type SavedScenario,
  saveScenario,
  type ScenarioContext,
} from "../../lib/api/scenario";
import { UnauthorizedError } from "../../lib/coreApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { describeError } from "../../lib/simulatorForm";
import { initialStep, type SimulatorStep } from "../../lib/simulatorStep";

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
    return { context, draft, scenarios, latestIncomeId };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

/** 초안이 받쳐 주지 못하는 단계로는 들어가지 않는다. 결과는 이직 입력이 있어야 한다. */
function reachableStep(
  step: SimulatorStep,
  draft: EvaluateRequest | null,
): SimulatorStep {
  if (!draft) return "current";
  if (step === "result" && !draft.move) return "stay";
  return step;
}

/** `request` 가 어느 입력에 대한 평가인지 함께 들고 있어, 초안이 바뀌면 낡은 결과로 알아본다. */
interface Evaluation {
  request: EvaluateRequest | null;
  result: EvaluationResponse | null;
  error: string | null;
}

const NO_EVALUATION: Evaluation = { request: null, result: null, error: null };

function useEvaluation(
  active: boolean,
  draft: EvaluateRequest | null,
  onUnauthorized: () => void,
) {
  const [evaluation, setEvaluation] = useState<Evaluation>(NO_EVALUATION);
  const stale = active && draft !== null && evaluation.request !== draft;
  const unauthorized = useRef(onUnauthorized);
  useEffect(() => {
    unauthorized.current = onUnauthorized;
  });

  useEffect(() => {
    if (!stale || !draft) return;
    let cancelled = false;
    evaluate(draft)
      .then((result) => {
        if (!cancelled) setEvaluation({ request: draft, result, error: null });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof UnauthorizedError) return unauthorized.current();
        setEvaluation((prev) => ({
          request: draft,
          result: prev.result,
          error: describeError(error),
        }));
      });
    return () => {
      cancelled = true;
    };
  }, [stale, draft]);

  return {
    evaluation,
    // 낡은 입력에 대한 실패 문장은 지금 입력과 무관하다.
    error: evaluation.request === draft ? evaluation.error : null,
    pending: stale,
    retry: () => setEvaluation(NO_EVALUATION),
  };
}

export default function DashboardSimulator() {
  const loaded = useLoaderData<typeof clientLoader>();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  const [context, setContext] = useState<ScenarioContext>(loaded.context);
  const [draft, setDraft] = useState<EvaluateRequest | null>(loaded.draft);
  const [scenarios, setScenarios] = useState<SavedScenario[]>(loaded.scenarios);
  const [moveValues, setMoveValues] = useState<MoveFormValues | undefined>();
  const [focus, setFocus] = useState<CurrentJobFocus | undefined>();
  const [notice, setNotice] = useState<string | null>(null);
  // 저장된 시나리오를 불러오는 것처럼 초안이 밖에서 바뀔 때 상단 입력칸을 새로 그린다.
  const [version, setVersion] = useState(0);
  // 초안 저장을 순서대로 보내, 늦게 끝난 앞선 요청이 최신 초안을 덮지 않게 한다.
  const putQueue = useRef<Promise<void>>(Promise.resolve());

  const step = reachableStep(initialStep(params, draft), draft);
  const goTo = (next: SimulatorStep) => setParams({ step: next });
  const toLogin = () => navigate(LOGIN_PATH, { replace: true });

  const { evaluation, error, pending, retry } = useEvaluation(
    step === "stay" || step === "result",
    draft,
    toLogin,
  );

  const replaceDraft = (next: EvaluateRequest, persist: boolean) => {
    setDraft(next);
    setNotice(null);
    if (!persist) return;
    putQueue.current = putQueue.current
      .then(() => putDraft(next))
      .then(() => undefined)
      .catch((error: unknown) => {
        if (error instanceof UnauthorizedError) return toLogin();
        setNotice(`초안을 저장하지 못했습니다: ${describeError(error)}`);
      });
  };

  const adopt = (next: EvaluateRequest, to: SimulatorStep) => {
    setVersion((v) => v + 1);
    replaceDraft(next, true);
    goTo(to);
  };

  const fill = (code: string) => {
    const target = focusTargetOf(code);
    setFocus(target);
    goTo(target ? "current" : "move");
  };

  const refreshContext = () => {
    getContext().then(setContext, (error: unknown) => {
      if (error instanceof UnauthorizedError) return toLogin();
      setNotice(
        `성과급 범위를 다시 불러오지 못했습니다: ${describeError(error)}`,
      );
    });
  };

  const save = async (name: string | null) => {
    if (!draft) return;
    const saved = await saveScenario(name, draft);
    setScenarios((prev) => [saved, ...prev]);
  };

  return (
    <PageFrame width="narrow">
      <PageHeader
        title="이직 시뮬레이터"
        description="남을 때와 옮길 때의 세후·실질 소득을 비교합니다."
      />
      <div data-page-section="" className="flex flex-col gap-6">
        <SimulatorSteps current={step} />
        {notice && (
          <p role="alert" className="text-sm text-carix-danger">
            {notice}
          </p>
        )}

        {step === "current" && (
          <CurrentJobStep
            context={context}
            latestIncomeId={loaded.latestIncomeId}
            draft={draft}
            focus={focus}
            onBonusRecorded={refreshContext}
            onDone={(saved) => {
              replaceDraft(saved, false);
              refreshContext();
              goTo("stay");
            }}
          />
        )}

        {step === "stay" && draft && (
          <StayView
            evaluation={evaluation}
            error={error}
            pending={pending}
            raisePercent={draft.stay.raisePercent}
            onRetry={retry}
            onFill={fill}
            onCompare={() => goTo("move")}
          />
        )}

        {step === "move" && draft && (
          <MoveConditionsStep
            draft={draft}
            initialValues={moveValues}
            onBack={(values) => {
              setMoveValues(values);
              goTo("stay");
            }}
            onDone={(saved) => {
              replaceDraft(saved, false);
              goTo("result");
            }}
          />
        )}

        {step === "result" && draft?.move && (
          <>
            {evaluation.result?.move ? (
              <ResultView
                key={version}
                draft={{ ...draft, move: draft.move }}
                result={{ ...evaluation.result, move: evaluation.result.move }}
                pending={pending}
                notice={error}
                scenarios={scenarios}
                onEdit={(next) => replaceDraft(next, true)}
                onFullConditions={() => goTo("current")}
                onFill={fill}
                onSave={save}
                onLoadScenario={(request) => adopt(request, "result")}
                onRecalculate={(request) => adopt(request, "current")}
              />
            ) : (
              <EvaluationFallback
                error={error}
                pending={pending}
                onRetry={retry}
              />
            )}
          </>
        )}
      </div>
    </PageFrame>
  );
}

function EvaluationFallback({
  error,
  pending,
  onRetry,
}: {
  error: string | null;
  pending: boolean;
  onRetry: () => void;
}) {
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

function StayView({
  evaluation,
  error,
  pending,
  raisePercent,
  onRetry,
  onFill,
  onCompare,
}: {
  evaluation: Evaluation;
  error: string | null;
  pending: boolean;
  raisePercent: number;
  onRetry: () => void;
  onFill: (code: string) => void;
  onCompare: () => void;
}) {
  if (!evaluation.result) {
    return (
      <EvaluationFallback error={error} pending={pending} onRetry={onRetry} />
    );
  }
  return (
    <>
      {pending && (
        <p role="status" className="text-sm text-muted-foreground">
          다시 계산하는 중
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-carix-danger">
          {error}
        </p>
      )}
      <StayResultStep
        result={evaluation.result}
        raisePercent={raisePercent}
        onFill={onFill}
        onCompare={onCompare}
      />
    </>
  );
}
