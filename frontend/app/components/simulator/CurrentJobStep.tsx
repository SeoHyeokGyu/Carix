import {
  type Ref,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from "react";

import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

import { updateBonusTerms } from "../../lib/api/income";
import { patchProfile } from "../../lib/api/profile";
import type {
  EvaluateRequest,
  RetirementPensionType,
  ScenarioContext,
} from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import {
  manInputToWon,
  raisePercentError,
  wonToManInput,
} from "../../lib/simulatorForm";
import { BonusRecordForm } from "./BonusRecordForm";
import { ManAmountField } from "./ManAmountField";
import { OptionalChips, OptionalGroup } from "./OptionalItems";
import {
  type EmitSource,
  type StepFormHandle,
  useDebouncedEmit,
} from "./useDebouncedEmit";
import { useOptionalGroups } from "./useOptionalGroups";
import {
  RecordSaveNote,
  type RecordSaveTracker,
  useRecordSave,
} from "./useRecordSave";
import { useRevealedErrors } from "./useRevealedErrors";

type OptionalKey = "performanceBonus" | "stock" | "hireDate";

const SAVED_NOTE = "마이페이지에 저장됨";
const BONUS_AMOUNT_REQUIRED = "연간 고정상여 금액을 입력해 주세요.";
const BONUS_IDLE_NOTE = "마이페이지 소득 이력에 저장됩니다";
const BONUS_UNSAVED_NOTE = "칸을 벗어나면 저장됩니다";
const PENSION_TYPE_OPTIONS: { value: RetirementPensionType; label: string }[] =
  [
    { value: "SEVERANCE_DB", label: "퇴직금·DB형" },
    { value: "DC", label: "DC형" },
    { value: "UNKNOWN", label: "모름" },
  ];

type Stay = EvaluateRequest["stay"];

interface BonusTerms {
  applyFromIncomeId: string;
  bonusIncluded: boolean;
  fixedBonusAnnual: number | null;
}

interface CurrentJobStepProps {
  ref?: Ref<StepFormHandle<Stay>>;
  context: ScenarioContext;
  /** 가장 최근 소득 행. 상여 조건은 여기부터 저장한다. 소득 이력이 없으면 null. */
  latestIncomeId: string | null;
  /** 이미 있는 계산 요청. 처음 그릴 때만 읽는다. */
  initial: EvaluateRequest | null;
  /** 유효한 입력. 처음 그릴 때 유효하면 `mount` 로 바로, 이후 편집은 400ms 뒤 `edit`. */
  onChange: (stay: Stay, source: EmitSource) => void;
  /** 편집·flush 결과가 무효일 때. 계산 요청은 직전 값으로 남고, 호출자가 결과를 흐리게 한다. */
  onInvalid?: () => void;
  /** 상여 조건·입사일·퇴직연금 유형 기록 저장이 진행 중인지. 진행 중에는 호출자가 평가를 미룬다. */
  onRecordSaving: (saving: boolean) => void;
  /** 상여 조건·입사일·퇴직연금 유형 기록 저장이 성공한 뒤. 호출자가 맥락을 다시 읽고 재평가한다. */
  onRecordSaved: () => void;
  /** 성과급 기록을 마이페이지에 더한 뒤. 호출자가 컨텍스트(성과급 범위)를 다시 읽는다. */
  onBonusRecorded?: () => void;
  /**
   * 화면의 "따로 받음" 금액이 아직 저장되지 않았는지. 금액은 칸을 벗어날 때 저장되고 계산은 저장된
   * 기록을 쓰므로, 그 전까지 호출자가 결과를 흐리게 둔다.
   */
  onBonusUnsaved?: (unsaved: boolean) => void;
}

export function CurrentJobStep({
  ref,
  context,
  latestIncomeId,
  initial,
  onChange,
  onInvalid,
  onRecordSaving,
  onRecordSaved,
  onBonusRecorded,
  onBonusUnsaved,
}: CurrentJobStepProps) {
  const stay = initial?.stay;
  const noIncome = context.currentAnnualGross === null;
  const [raise, setRaise] = useState(
    String(stay?.raisePercent ?? context.defaultRaisePercent ?? ""),
  );
  const [overrideOpen, setOverrideOpen] = useState(
    stay?.currentGrossOverride != null,
  );
  const [override, setOverride] = useState(
    wonToManInput(stay?.currentGrossOverride),
  );
  const [bonusIncluded, setBonusIncluded] = useState(context.bonusIncluded);
  const [bonusAmount, setBonusAmount] = useState(
    wonToManInput(context.fixedBonusAnnual),
  );
  const [estimate, setEstimate] = useState(
    wonToManInput(stay?.performanceBonusEstimate),
  );
  const [vesting, setVesting] = useState(wonToManInput(stay?.stockVesting12m));
  const [unvested, setUnvested] = useState(wonToManInput(stay?.stockUnvested));
  const [hireDate, setHireDate] = useState("");
  const [pensionType, setPensionType] = useState(context.retirementPensionType);
  const { errors, setErrors, touch, revealAll, pickRevealed } =
    useRevealedErrors();
  const formRef = useRef<HTMLFormElement>(null);
  const optional = useOptionalGroups<OptionalKey>([
    // 기록 범위가 있으면 예상치는 쓰이지 않는다 — 그때 성과급 칸은 기록 추가 폼일 뿐이다.
    ...(estimate && !context.performanceBonusRange
      ? (["performanceBonus"] as const)
      : []),
    ...(vesting || unvested ? (["stock"] as const) : []),
  ]);

  const callbacks = useRef({ onRecordSaving, onRecordSaved, onBonusUnsaved });
  useEffect(() => {
    callbacks.current = { onRecordSaving, onRecordSaved, onBonusUnsaved };
  });
  // 상여 조건·입사일·퇴직연금 유형 저장을 함께 센다. 모두 끝나야 평가 보류를 푼다.
  const inFlight = useRef(0);
  const tracker: RecordSaveTracker = {
    begin: () => {
      if (inFlight.current++ === 0) callbacks.current.onRecordSaving(true);
    },
    end: (succeeded) => {
      if (succeeded) callbacks.current.onRecordSaved();
      if (--inFlight.current === 0) callbacks.current.onRecordSaving(false);
    },
  };
  // 저장된 금액은 칸과 같은 만원 단위로 맞춰 둔다. 만원 단위가 아닌 기록을 손대지 않은 칸과 다르다고 보면
  // 결과가 흐려지고, 칸을 벗어날 때 반올림한 값이 기록에 저장된다.
  const bonusSave = useRecordSave<BonusTerms>(
    updateBonusTerms,
    tracker,
    latestIncomeId === null
      ? null
      : {
          applyFromIncomeId: latestIncomeId,
          bonusIncluded: context.bonusIncluded,
          fixedBonusAnnual: context.bonusIncluded
            ? null
            : manInputToWon(wonToManInput(context.fixedBonusAnnual)),
        },
  );
  const hireDateSave = useRecordSave<string>(
    (value) => patchProfile({ hireDate: value }),
    tracker,
    null,
  );

  const pensionTypeSave = useRecordSave<RetirementPensionType>(
    (value) => patchProfile({ retirementPensionType: value }),
    tracker,
    context.retirementPensionType,
  );

  const range = context.performanceBonusRange;
  // 저장 중·저장 뒤에도 결과 표시를 위해 칸을 남긴다. 맥락을 다시 읽으면 입사일이 생긴다.
  const hireDateAsked =
    context.hireDate === null || hireDateSave.status !== "idle";
  const overrideShown = noIncome || overrideOpen;
  // 덮어쓴 연봉은 상여까지 더한 금액이라 상여 조건을 따로 묻지도 저장하지도 않는다.
  const overrideActive = overrideShown && manInputToWon(override) !== null;
  const bonusAsked = latestIncomeId !== null && !overrideActive;
  const bonusAmountWon = manInputToWon(bonusAmount);
  // 빈 금액은 칸 오류로 이미 무효라 따로 세지 않는다.
  const bonusUnsaved =
    latestIncomeId !== null &&
    !overrideActive &&
    !bonusIncluded &&
    bonusAmountWon !== null &&
    !bonusSave.isRequested({
      applyFromIncomeId: latestIncomeId,
      bonusIncluded: false,
      fixedBonusAnnual: bonusAmountWon,
    });

  useEffect(() => {
    callbacks.current.onBonusUnsaved?.(bonusUnsaved);
  }, [bonusUnsaved]);

  const validate = (): FieldErrors => {
    const next: FieldErrors = {};
    const raiseError = raisePercentError(raise);
    if (raiseError) next.raisePercent = raiseError;
    if (overrideShown && manInputToWon(override) === null) {
      next.override = "세전 연봉을 입력해 주세요.";
    }
    if (
      !overrideActive &&
      !bonusIncluded &&
      manInputToWon(bonusAmount) === null
    ) {
      next.bonusAmount = BONUS_AMOUNT_REQUIRED;
    }
    return next;
  };

  const compute = (): Stay | null => {
    const found = validate();
    setErrors(pickRevealed(found));
    if (Object.keys(found).length > 0) {
      onInvalid?.();
      return null;
    }
    return {
      raisePercent: Number(raise),
      currentGrossOverride: overrideShown ? manInputToWon(override) : null,
      performanceBonusEstimate: range ? null : manInputToWon(estimate),
      stockVesting12m: manInputToWon(vesting),
      stockUnvested: manInputToWon(unvested),
    };
  };

  const debounced = useDebouncedEmit(
    compute,
    [
      raise,
      overrideShown,
      override,
      bonusIncluded,
      bonusAmount,
      estimate,
      vesting,
      unvested,
      range !== null,
    ],
    onChange,
  );

  /** "따로 받음" 금액을 저장한다. 금액이 비면 저장하지 않고 금액 칸 오류. */
  const submitSeparateBonus = () => {
    if (latestIncomeId === null) return;
    const won = manInputToWon(bonusAmount);
    if (won === null) {
      touch("bonusAmount");
      setErrors((prev) => ({ ...prev, bonusAmount: BONUS_AMOUNT_REQUIRED }));
      return;
    }
    bonusSave.submit({
      applyFromIncomeId: latestIncomeId,
      bonusIncluded: false,
      fixedBonusAnnual: won,
    });
  };

  const chooseBonusIncluded = (included: boolean) => {
    setBonusIncluded(included);
    if (!included) {
      submitSeparateBonus();
      return;
    }
    setErrors((prev) => ({ ...prev, bonusAmount: undefined }));
    if (latestIncomeId === null) return;
    bonusSave.submit({
      applyFromIncomeId: latestIncomeId,
      bonusIncluded: true,
      fixedBonusAnnual: null,
    });
  };

  /** 금액 칸을 벗어날 때와 flush 때. */
  const commitBonusAmount = () => {
    if (bonusAsked && !bonusIncluded) submitSeparateBonus();
  };

  const commitHireDate = () => {
    if (hireDate) hireDateSave.submit(hireDate);
  };

  const flush = (): Stay | null => {
    commitBonusAmount();
    commitHireDate();
    revealAll();
    const value = debounced.flush();
    if (value === null) {
      setErrors(validate());
      requestAnimationFrame(() => focusFirstInvalidField(formRef.current));
    }
    return value;
  };

  useImperativeHandle(ref, () => ({ flush }));

  const handleKeyDown = (event: React.KeyboardEvent<HTMLFormElement>) => {
    if (event.key !== "Enter" || !(event.target instanceof HTMLInputElement)) {
      return;
    }
    event.preventDefault();
    flush();
  };

  return (
    <form
      ref={formRef}
      onSubmit={(event) => event.preventDefault()}
      onKeyDown={handleKeyDown}
      noValidate
      className="flex flex-col gap-6"
    >
      {!noIncome && (
        // 바꾸기·취소는 연봉 줄 오른쪽 한 자리에서 토글한다. 따로 줄을 두면 버튼이 홀로 떠 보인다.
        <div className="flex flex-wrap items-center justify-between gap-x-3 text-sm">
          <p>
            세전 연봉{" "}
            <strong>{formatKoreanSalary(context.currentAnnualGross)}</strong>
            {context.hireDate && <> · 입사일 {context.hireDate}</>}
          </p>
          <Button
            type="button"
            variant="link"
            className="min-h-11 px-0 sm:min-h-0"
            aria-label={overrideOpen ? "이번만 바꾸기 취소" : undefined}
            onClick={() => {
              if (!overrideOpen) {
                setOverrideOpen(true);
                return;
              }
              setOverrideOpen(false);
              setOverride("");
              setErrors((prev) => ({ ...prev, override: undefined }));
            }}
          >
            {overrideOpen ? "취소" : "이번만 바꾸기"}
          </Button>
        </div>
      )}

      {overrideShown && (
        <ManAmountField
          id="override"
          label={
            noIncome
              ? "현재 세전 연봉 (만원)"
              : "이번만 적용할 세전 연봉 (만원)"
          }
          hint={
            noIncome
              ? "소득 이력이 없어 직접 입력합니다. 저장되지 않습니다."
              : "따로 받는 고정상여가 있으면 더한 연 금액입니다. 이번 계산에만 쓰고 저장하지 않습니다."
          }
          value={override}
          onChange={(v) => {
            touch("override");
            setOverride(v);
          }}
          error={errors.override}
        />
      )}

      <Field
        id="raisePercent"
        label="올해 예상 인상률 (%)"
        error={errors.raisePercent}
      >
        {(aria) => (
          <Input
            {...aria}
            name="raisePercent"
            type="text"
            inputMode="decimal"
            placeholder="예: 3.5"
            value={raise}
            onChange={(e) => {
              touch("raisePercent");
              setRaise(e.target.value);
            }}
          />
        )}
      </Field>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">
          퇴직연금 유형{" "}
          <RecordSaveNote
            status={pensionTypeSave.status}
            target="프로필"
            staleNote="결과는 이전 답 기준입니다."
            onRetry={pensionTypeSave.retry}
          />
        </legend>
        <div className="flex flex-wrap gap-x-4 gap-y-2">
          {PENSION_TYPE_OPTIONS.map((option) => (
            <label
              key={option.value}
              className="flex items-center gap-2 text-sm"
            >
              <input
                type="radio"
                name="retirementPensionType"
                checked={pensionType === option.value}
                onChange={() => {
                  setPensionType(option.value);
                  pensionTypeSave.submit(option.value);
                }}
              />
              {option.label}
            </label>
          ))}
        </div>
      </fieldset>

      {latestIncomeId && overrideActive && (
        <p className="text-sm text-muted-foreground">
          이번만 바꾼 연봉은 따로 받는 고정상여까지 더한 연 금액으로 계산합니다.
        </p>
      )}
      {latestIncomeId && !overrideActive && (
        <>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-medium">
              연봉 계약 금액에 고정상여가 들어 있나요?{" "}
              <RecordSaveNote
                status={bonusUnsaved ? "idle" : bonusSave.status}
                target="소득 이력"
                idleNote={bonusUnsaved ? BONUS_UNSAVED_NOTE : BONUS_IDLE_NOTE}
                staleNote="결과는 이전 답 기준입니다."
                onRetry={bonusSave.retry}
              />
            </legend>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="bonusIncluded"
                checked={bonusIncluded}
                onChange={() => chooseBonusIncluded(true)}
              />
              들어 있음
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="bonusIncluded"
                checked={!bonusIncluded}
                onChange={() => chooseBonusIncluded(false)}
              />
              따로 받음
            </label>
          </fieldset>
          {!bonusIncluded && (
            <div onBlur={commitBonusAmount}>
              <ManAmountField
                id="bonusAmount"
                label="연간 고정상여 (만원)"
                value={bonusAmount}
                onChange={(v) => {
                  touch("bonusAmount");
                  setBonusAmount(v);
                }}
                error={errors.bonusAmount}
              />
            </div>
          )}
        </>
      )}

      {range && (
        <p className="text-sm">
          성과급 최근 {range.years}개년 최저 {formatKoreanSalary(range.min)} ·
          평균 {formatKoreanSalary(range.average)}
        </p>
      )}
      {optional.isAdded("performanceBonus") && (
        <OptionalGroup
          label={range ? "성과급 기록" : "성과급"}
          groupRef={optional.register("group", "performanceBonus")}
          // 기록 폼만 있을 때는 비울 값이 없다 — `빼기` 가 저장된 기록을 지우는 것처럼 읽히지 않게 `닫기`.
          removeText={range ? "닫기" : "빼기"}
          onRemove={() => {
            setEstimate("");
            optional.remove("performanceBonus");
          }}
        >
          {!range && (
            <ManAmountField
              id="performanceBonusEstimate"
              label="이번 시나리오용 성과급 예상치 (만원)"
              hint="이번 계산에만 쓰고 소득 이력에는 저장하지 않습니다."
              value={estimate}
              onChange={setEstimate}
            />
          )}
          <BonusRecordForm
            savedNote={SAVED_NOTE}
            onRecorded={onBonusRecorded}
          />
        </OptionalGroup>
      )}
      {optional.isAdded("stock") && (
        <OptionalGroup
          label="주식"
          groupRef={optional.register("group", "stock")}
          onRemove={() => {
            setVesting("");
            setUnvested("");
            optional.remove("stock");
          }}
        >
          <ManAmountField
            id="stockVesting12m"
            label="1년 안에 받을 주식 (만원)"
            value={vesting}
            onChange={setVesting}
          />
          <ManAmountField
            id="stockUnvested"
            label="아직 못 받은 주식 (만원)"
            hint="새 회사로 옮기면 받지 못하는 몫입니다."
            value={unvested}
            onChange={setUnvested}
          />
        </OptionalGroup>
      )}
      {hireDateAsked && optional.isAdded("hireDate") && (
        <OptionalGroup
          label="입사일"
          groupRef={optional.register("group", "hireDate")}
          // 저장이 나간 입사일은 되돌릴 수 없으니 뺄 수 있는 것처럼 보이지 않는다. 빼면 값도 비워야
          // flush 가 입사일을 저장하지 않는다.
          onRemove={
            hireDateSave.status === "idle"
              ? () => {
                  setHireDate("");
                  optional.remove("hireDate");
                }
              : undefined
          }
        >
          <div className="flex flex-col gap-1">
            <Field
              id="hireDate"
              label="입사일"
              hint="입력하면 프로필에 저장됩니다. 퇴직금 계산에 쓰입니다."
            >
              {(aria) => (
                <Input
                  {...aria}
                  type="date"
                  value={hireDate}
                  onChange={(e) => setHireDate(e.target.value)}
                  onBlur={commitHireDate}
                />
              )}
            </Field>
            <RecordSaveNote
              status={hireDateSave.status}
              target="프로필"
              onRetry={hireDateSave.retry}
            />
          </div>
        </OptionalGroup>
      )}
      <OptionalChips
        items={[
          ...(optional.isAdded("performanceBonus")
            ? []
            : [
                {
                  key: "performanceBonus" as const,
                  label: range ? "성과급 기록" : "성과급",
                },
              ]),
          ...(optional.isAdded("stock")
            ? []
            : [{ key: "stock" as const, label: "주식" }]),
          ...(context.hireDate === null && !optional.isAdded("hireDate")
            ? [{ key: "hireDate" as const, label: "입사일" }]
            : []),
        ]}
        register={optional.register}
        onAdd={optional.add}
      />
    </form>
  );
}
