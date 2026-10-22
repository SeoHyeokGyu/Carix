import { useRef, useState } from "react";
import {
  redirect,
  useLoaderData,
  useNavigate,
  useSearchParams,
} from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";

import { CurrentJobStep } from "../../components/simulator/CurrentJobStep";
import {
  MoveConditionsStep,
  type MoveFormValues,
} from "../../components/simulator/MoveConditionsStep";
import { ResultView } from "../../components/simulator/ResultView";
import { SavedScenarioList } from "../../components/simulator/SavedScenarioList";
import { SimulatorSteps } from "../../components/simulator/SimulatorSteps";
import {
  EvaluationFallback,
  StayView,
} from "../../components/simulator/StayView";
import { useEvaluation } from "../../components/simulator/useEvaluation";
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
import { LOGIN_PATH } from "../../lib/routeGuards";
import { type FillTarget, fillTargetOf } from "../../lib/simulatorFocus";
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
    // 초안 없이 다시 온 사용자는 가장 최근 새 방식 저장분으로 들어간다. 목록은 이미 최근 순이다.
    const adopted =
      draft ??
      scenarios.find((s) => s.engineVersion === 2 && s.request)?.request ??
      null;
    return { context, draft: adopted, scenarios, latestIncomeId };
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

export default function DashboardSimulator() {
  const loaded = useLoaderData<typeof clientLoader>();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  const [context, setContext] = useState<ScenarioContext>(loaded.context);
  const [draft, setDraft] = useState<EvaluateRequest | null>(loaded.draft);
  const [scenarios, setScenarios] = useState<SavedScenario[]>(loaded.scenarios);
  const [moveValues, setMoveValues] = useState<MoveFormValues | undefined>();
  const [focus, setFocus] = useState<FillTarget | undefined>();
  const [notice, setNotice] = useState<string | null>(null);
  // 저장된 시나리오를 불러오는 것처럼 초안이 밖에서 바뀔 때 상단 입력칸을 새로 그린다.
  const [version, setVersion] = useState(0);
  // 초안 저장을 순서대로 보내, 늦게 끝난 앞선 요청이 최신 초안을 덮지 않게 한다.
  const putQueue = useRef<Promise<void>>(Promise.resolve());

  const step = reachableStep(initialStep(params, draft), draft);
  /** 넣기로 갈 때만 포커스를 싣는다. 다른 이동은 남은 포커스를 비운다. */
  const goTo = (next: SimulatorStep, nextFocus?: FillTarget) => {
    setFocus(nextFocus);
    setParams({ step: next });
  };
  const toLogin = () => navigate(LOGIN_PATH, { replace: true });

  const { evaluation, error, pending, retry } = useEvaluation(
    step === "stay" || step === "result",
    draft,
    toLogin,
  );

  /** 앞선 저장 뒤에 이어 보낸다. 실패는 호출자에게 던지되 뒤 저장의 줄은 끊지 않는다. */
  const queueDraft = (next: EvaluateRequest): Promise<void> => {
    const run = putQueue.current.then(() => putDraft(next)).then(() => {});
    putQueue.current = run.catch(() => {});
    return run;
  };

  const replaceDraft = (next: EvaluateRequest, persist: boolean) => {
    setDraft(next);
    setNotice(null);
    if (!persist) return;
    queueDraft(next).catch((error: unknown) => {
      if (error instanceof UnauthorizedError) return toLogin();
      setNotice(`초안을 저장하지 못했습니다: ${describeError(error)}`);
    });
  };

  const adopt = (next: EvaluateRequest, to: SimulatorStep) => {
    setVersion((v) => v + 1);
    setMoveValues(undefined);
    replaceDraft(next, true);
    goTo(to);
  };

  const fill = (code: string) => {
    const target = fillTargetOf(code);
    if (target) goTo(target.step, target);
  };

  const refreshContext = () => {
    getContext().then(setContext, (error: unknown) => {
      if (error instanceof UnauthorizedError) return toLogin();
      setNotice(
        `성과급 범위를 다시 불러오지 못했습니다: ${describeError(error)}`,
      );
    });
  };

  const save = async (name: string | null, request: EvaluateRequest) => {
    const saved = await saveScenario(name, request);
    setScenarios((prev) => [saved, ...prev]);
  };

  const savedList = (
    <SavedScenarioList
      scenarios={scenarios}
      onLoad={(request) => adopt(request, request.move ? "result" : "stay")}
      onRecalculate={(request) => adopt(request, "current")}
    />
  );
  // 저장분이 이전 방식뿐이라 채택할 초안이 없으면 ① 위에 목록을 먼저 보인다
  const listOnTop = draft === null && scenarios.length > 0;

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
          <>
            {listOnTop && savedList}
            <CurrentJobStep
              key={version}
              context={context}
              latestIncomeId={loaded.latestIncomeId}
              draft={draft}
              focus={focus?.step === "current" ? focus.field : undefined}
              saveDraft={queueDraft}
              onBonusRecorded={refreshContext}
              onDone={(saved) => {
                replaceDraft(saved, false);
                refreshContext();
                goTo("stay");
              }}
            />
            {!listOnTop && savedList}
          </>
        )}

        {step === "stay" && draft && (
          <>
            <StayView
              evaluation={evaluation}
              error={error}
              pending={pending}
              raisePercent={draft.stay.raisePercent}
              onRetry={retry}
              onFill={fill}
              onCompare={() => goTo("move")}
              onSave={(name) => save(name, { stay: draft.stay, move: null })}
            />
            {savedList}
          </>
        )}

        {step === "move" && draft && (
          <MoveConditionsStep
            key={version}
            draft={draft}
            initialValues={moveValues}
            focus={focus?.step === "move" ? focus.field : undefined}
            saveDraft={queueDraft}
            onBack={(values) => {
              setMoveValues(values);
              goTo("stay");
            }}
            onDone={(saved) => {
              replaceDraft(saved, false);
              setMoveValues(undefined);
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
                onEdit={(next) => {
                  setMoveValues(undefined);
                  replaceDraft(next, true);
                }}
                onFullConditions={() => goTo("current")}
                onFill={fill}
                onSave={(name) => save(name, draft)}
                onLoadScenario={(request) =>
                  adopt(request, request.move ? "result" : "stay")
                }
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
