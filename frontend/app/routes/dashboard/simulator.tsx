import { type ReactNode, useId, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { redirect, useLoaderData, useNavigate } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, CardTitle } from "@/components/ui/card";
import { InlineAlert } from "@/components/ui/inline-alert";
import { cn } from "@/lib/utils";

import { AssumptionsPanel } from "../../components/simulator/AssumptionsPanel";
import { CompareResult } from "../../components/simulator/CompareResult";
import { CurrentJobStep } from "../../components/simulator/CurrentJobStep";
import { EvaluationFallback } from "../../components/simulator/EvaluationFallback";
import { InflationBasisNote } from "../../components/simulator/InflationBasisNote";
import {
  BLANK_OFFER,
  MoveConditionsStep,
  type OfferEmit,
} from "../../components/simulator/MoveConditionsStep";
import { OfferCard } from "../../components/simulator/OfferCard";
import { OverrideNotice } from "../../components/simulator/OverrideNotice";
import { SavedScenarioMenu } from "../../components/simulator/SavedScenarioMenu";
import { SaveScenarioButton } from "../../components/simulator/SaveScenarioButton";
import { StayOutcome } from "../../components/simulator/StayOutcome";
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
import {
  compareHint,
  type OfferMissing,
  splitAssumptions,
} from "../../lib/simulatorCompare";
import { type FillTarget, fillTargetOf } from "../../lib/simulatorFocus";
import { describeError } from "../../lib/simulatorForm";

type Stay = EvaluateRequest["stay"];
type Move = NonNullable<EvaluateRequest["move"]>;

export function meta() {
  return [
    { title: "이직 시뮬레이터 | Carix" },
    {
      name: "description",
      content: SUBTITLE,
    },
  ];
}

/** 이 라우트가 직접 상태를 들고 있으므로 쿼리가 바뀌어도 다시 불러오지 않는다. */
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
  /** 이 영역의 결과가 있으면 그린다. 없으면 대체 표시. */
  children: ReactNode | null;
  invalid: boolean;
  dimmed: boolean;
  pending: boolean;
  /** 평가 오류. 두 영역이 같은 오류를 두 번 알리지 않도록 한 영역에만 넘긴다. */
  error: string | null;
  onRetry: () => void;
  /** 오류 자체는 다른 영역이 알릴 때, 이 영역의 직전 결과가 지금 입력의 결과가 아님을 적는 한 줄. */
  failedNote?: string | null;
  /** `다시 계산하는 중` 을 스크린리더에 알릴지. 두 영역이 같은 문장을 함께 알리지 않도록 한 곳만 켠다. */
  announcePending?: boolean;
}

/** 입력 아래 결과 자리. 다시 계산하는 동안·입력이 무효인 동안 직전 결과를 흐리게 남긴다. */
function ResultArea({
  hint,
  children,
  invalid,
  dimmed,
  pending,
  error,
  onRetry,
  failedNote = null,
  announcePending = true,
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
          <p
            role={announcePending ? "status" : undefined}
            className="text-sm text-muted-foreground"
          >
            다시 계산하는 중
          </p>
        )
      )}
      {error && <InlineAlert message={error} onRetry={onRetry} />}
      {failedNote && <p className="text-sm text-carix-danger">{failedNote}</p>}
      <div className={cn("transition-opacity", dimmed && "opacity-60")}>
        {children}
      </div>
    </div>
  );
}

const SUBTITLE =
  "지금 회사와 받은 오퍼를 세후·실질 소득으로 나란히 비교합니다.";
const SAVED_MESSAGE = "시나리오를 저장했습니다.";
const NO_STAY_HINT =
  "올해 예상 인상률과 연봉을 넣으면 지금 회사 결과가 나옵니다";

export default function DashboardSimulator() {
  const loaded = useLoaderData<typeof clientLoader>();
  const navigate = useNavigate();
  const currentHeadingId = useId();

  const [context, setContext] = useState<ScenarioContext>(loaded.context);
  const [scenarios, setScenarios] = useState<SavedScenario[]>(loaded.scenarios);
  // 계산 요청. 렌더용 상태와, 같은 이벤트 안에서 바로 읽을 ref 를 함께 둔다.
  const [request, setRequest] = useState<EvaluateRequest | null>(loaded.draft);
  const requestRef = useRef(request);
  // 지금 회사 계산 요청이 아직 없을 때(소득 이력이 없어 연봉을 안 넣음) 먼저 채운 오퍼. 두 카드가 함께 보여
  // 오퍼부터 채우는 사람이 있으므로 버리지 않고 첫 지금 회사 값과 합친다.
  const pendingOffer = useRef<Move | null>(null);
  // 들어오기만 해서는 서버 초안을 쓰지 않는다. 처음 값을 바꾸거나 저장 버튼을 누르면 켠다.
  const persisted = useRef(false);
  const [queueDraft] = useState(() => createDraftQueue(putDraft));
  // 불러오기·되돌리기·지우기로 계산 요청이 밖에서 바뀔 때 두 카드 입력칸을 새로 그린다.
  const [version, setVersion] = useState(0);
  const [revision, setRevision] = useState(0);
  const [offerOpen, setOfferOpen] = useState(() => loaded.draft?.move != null);
  const [recordSaving, setRecordSaving] = useState(false);
  // 지금 회사의 "따로 받음" 금액이 저장 전이면 결과는 이전 기록 기준이다.
  const [bonusUnsaved, setBonusUnsaved] = useState(false);
  const [invalid, setInvalid] = useState({ current: false, offer: false });
  const [offerMissing, setOfferMissing] = useState<OfferMissing | null>(null);
  const [focus, setFocus] = useState<FillTarget | undefined>();
  const [notice, setNotice] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const currentForm = useRef<StepFormHandle<Stay>>(null);
  const offerForm = useRef<StepFormHandle<OfferEmit>>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const toLogin = () => navigate(LOGIN_PATH, { replace: true });

  const { evaluation, error, pending, retry } = useEvaluation(
    request !== null,
    request,
    toLogin,
    { revision, paused: recordSaving },
  );

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
    setSavedMessage(null);
  };

  /** 저장 버튼을 누른 때부터 초안을 저장한다. 처음이면 지금 계산 요청을 바로 저장한다. */
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

  const markValid = (which: keyof typeof invalid) =>
    setInvalid((prev) => (prev[which] ? { ...prev, [which]: false } : prev));
  const markInvalid = (which: keyof typeof invalid) =>
    setInvalid((prev) => (prev[which] ? prev : { ...prev, [which]: true }));

  const onStayChange = (stay: Stay, source: EmitSource) => {
    markValid("current");
    const move = requestRef.current?.move ?? pendingOffer.current;
    pendingOffer.current = null;
    update({ stay, move }, source);
  };

  const onOfferChange = (offer: OfferEmit, source: EmitSource) => {
    markValid("offer");
    setOfferMissing(null);
    const move = offer === BLANK_OFFER ? null : offer;
    const prev = requestRef.current;
    if (prev) update({ ...prev, move }, source);
    else pendingOffer.current = move;
  };

  const onOfferInvalid = (missing: OfferMissing) => {
    markInvalid("offer");
    setOfferMissing(missing);
  };

  const fill = (code: string) => {
    const target = fillTargetOf(code);
    if (!target) return;
    // 새로 마운트되는 오퍼 입력은 마운트 때 그 칸을 펼치고 포커스한다.
    if (target.card === "offer" && !offerOpen) setOfferOpen(true);
    // 같은 칸을 다시 눌러도 포커스가 가도록 한 번 비웠다가 넣는다.
    flushSync(() => setFocus(undefined));
    setFocus(target);
  };

  /** 대기 중인 편집을 반영한다. 무효면 false — flush 가 첫 오류 칸으로 포커스한다. */
  const flushForms = () =>
    currentForm.current?.flush() != null &&
    (!offerOpen || offerForm.current?.flush() != null);

  const openSave = () => {
    // 같은 문장을 다시 넣으면 다음 저장 성공이 알려지지 않는다.
    setSavedMessage(null);
    if (!flushForms()) return false;
    startPersisting();
    return true;
  };

  const save = async (name: string | null) => {
    if (!flushForms()) return false;
    const current = requestRef.current;
    if (!current) return false;
    // 비어 있는 열린 카드는 flush 로 이미 move 가 null 이다.
    const payload = {
      stay: current.stay,
      move: offerOpen ? (current.move ?? null) : null,
    };
    const saved = await saveScenario(name, payload);
    setScenarios((prev) => [saved, ...prev]);
    setSavedMessage(SAVED_MESSAGE);
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

  /** 불러오기·되돌리기·지우기. 남아 있던 입력값은 버리고 계산 요청으로 두 카드를 새로 그린다. */
  const replaceRequest = (next: EvaluateRequest | null) => {
    setVersion((v) => v + 1);
    setInvalid({ current: false, offer: false });
    setOfferMissing(null);
    setOfferOpen(next?.move != null);
    // 다시 그려지는 입력이 지난 `넣기` 대상을 마운트 때 다시 잡아 포커스를 빼앗지 않게 한다.
    setFocus(undefined);
    pendingOffer.current = null;
    commit(next);
    persisted.current = true;
    // NOTE: (cjkang) 초안 삭제 API 가 없어 null 로 되돌릴 때는 여기서 서버 초안을 쓰지 않는다. 다만 이미
    // persisted 라, 새로 그려진 지금 회사 카드가 기본 인상률로 유효하면 그 mount 값이 서버 초안을 덮는다.
    // 다시 들어올 때 불러왔던 저장분이 되살아나는 것은 기본 인상률이 없어 지금 회사 입력이 무효일 때뿐이다.
    if (next) saveDraft(next);
  };
  // 알림 안에 포커스가 있는 채로 사라지면 포커스가 body 로 빠진다. 페이지 제목으로 옮긴다.
  const undoNoticeRef = useRef<HTMLDivElement>(null);
  const keepFocusOffNotice = () => {
    if (undoNoticeRef.current?.contains(document.activeElement)) {
      titleRef.current?.focus();
    }
  };
  const {
    adopt,
    undo,
    notice: adoptNotice,
  } = useUndoableAdopt(replaceRequest, keepFocusOffNotice);

  const load = (next: EvaluateRequest, title: string) => {
    adopt(
      withoutStaleOverride(next, context),
      requestRef.current,
      `「${title}」을 불러왔습니다.`,
    );
    // 메뉴는 고른 뒤 포커스를 트리거로 돌려주지 않는다.
    titleRef.current?.focus();
  };

  const clearOffer = () => {
    const current = requestRef.current;
    // 계산 요청에 담긴 오퍼가 없으면 되돌릴 것도 없다. 카드만 닫는다.
    if (!current?.move) {
      pendingOffer.current = null;
      setInvalid((prev) => ({ ...prev, offer: false }));
      setOfferMissing(null);
      setFocus(undefined);
      setOfferOpen(false);
      return;
    }
    adopt(
      { stay: current.stay, move: null },
      current,
      "새 회사 오퍼를 지웠습니다.",
    );
  };

  const stayDimmed = pending || recordSaving || bonusUnsaved || invalid.current;
  const result = evaluation.result;
  // 비교 결과를 그릴 때만 그 결론이 알림 자리가 된다. 알림(aria-live)은 화면에 한 곳뿐이다.
  const compare =
    offerOpen && request?.move && result?.move
      ? { ...result, move: result.move }
      : null;
  // 지금 회사 요청이 없으면 그쪽 안내가 먼저다. 먼저 채운 오퍼에 다시 넣으라고 하지 않는다.
  const offerHint =
    request === null || request.move ? null : compareHint(false, offerMissing);
  const saveButton = (variant: "default" | "outline") => (
    <SaveScenarioButton
      onOpen={openSave}
      onSave={save}
      blocked={pending || recordSaving}
      variant={variant}
    />
  );

  return (
    <PageFrame width="wide">
      <PageHeader
        title="이직 시뮬레이터"
        titleRef={titleRef}
        description={SUBTITLE}
        actions={
          <>
            {scenarios.length > 0 && (
              <SavedScenarioMenu
                scenarios={scenarios}
                onLoad={load}
                onRecalculate={load}
              />
            )}
            {saveButton("default")}
          </>
        }
      />
      <div data-page-section="" className="flex flex-col gap-6">
        {adoptNotice && (
          <div ref={undoNoticeRef}>
            <InlineAlert
              tone="info"
              message={adoptNotice.message}
              action={{
                label: "이전 입력으로 되돌리기",
                onClick: () => {
                  keepFocusOffNotice();
                  undo();
                },
              }}
            />
          </div>
        )}
        {notice && (
          <InlineAlert message={notice} onDismiss={() => setNotice(null)} />
        )}
        {savedMessage && (
          <p role="status" className="text-sm text-muted-foreground">
            {savedMessage}
          </p>
        )}

        <div className="grid items-start gap-5 md:grid-cols-2">
          <section aria-labelledby={currentHeadingId}>
            <Card>
              <CardTitle as="h2" id={currentHeadingId}>
                지금 회사
              </CardTitle>
              <CurrentJobStep
                key={version}
                ref={currentForm}
                context={context}
                latestIncomeId={loaded.latestIncomeId}
                initial={request}
                focus={focus?.card === "current" ? focus.field : undefined}
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
                hint={request === null ? NO_STAY_HINT : null}
                invalid={invalid.current}
                dimmed={stayDimmed}
                pending={pending}
                error={error}
                onRetry={retry}
              >
                {result && request && (
                  <>
                    <OverrideNotice gross={request.stay.currentGrossOverride} />
                    <StayOutcome
                      result={result}
                      raisePercent={request.stay.raisePercent}
                      onFill={fill}
                      live={compare === null}
                    />
                  </>
                )}
              </ResultArea>
            </Card>
          </section>

          <OfferCard
            open={offerOpen}
            onOpen={() => setOfferOpen(true)}
            onClear={clearOffer}
          >
            <MoveConditionsStep
              key={version}
              ref={offerForm}
              initial={request}
              focus={focus?.card === "offer" ? focus.field : undefined}
              onChange={onOfferChange}
              onInvalid={onOfferInvalid}
            />
          </OfferCard>
        </div>

        {/* 계산 중·오류 대체 표시는 남을 때 결과 자리가 맡는다. 비교 자리는 안내나 결과가 있을 때만. */}
        {offerOpen && (offerHint !== null || compare !== null) && (
          <ResultArea
            hint={offerHint}
            invalid={invalid.offer}
            dimmed={stayDimmed || invalid.offer || error !== null}
            pending={pending}
            error={null}
            failedNote={
              error !== null
                ? "계산하지 못했습니다 — 위 오류를 확인하세요"
                : null
            }
            onRetry={retry}
            announcePending={false}
          >
            {compare && (
              <CompareResult
                result={compare}
                expectedRaisePercent={request?.stay.raisePercent ?? 0}
                onFill={fill}
              />
            )}
          </ResultArea>
        )}

        {result && (
          <>
            <InflationBasisNote
              inflation={result.inflation}
              stay={result.stay}
            />
            <AssumptionsPanel
              assumptions={splitAssumptions(result.assumptions).rest}
            />
          </>
        )}

        <div className="sticky bottom-0 -mx-6 flex justify-end border-t border-carix-border bg-carix-bg/95 px-6 py-3 backdrop-blur-md sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:px-0 sm:py-0 sm:backdrop-blur-none">
          {saveButton("outline")}
        </div>
      </div>
    </PageFrame>
  );
}
