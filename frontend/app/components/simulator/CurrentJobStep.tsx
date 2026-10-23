import {
  type Ref,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { updateBonusTerms } from "../../lib/api/income";
import { patchProfile } from "../../lib/api/profile";
import type { EvaluateRequest, ScenarioContext } from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import type { CurrentJobFocus } from "../../lib/simulatorFocus";
import {
  manInputToWon,
  raisePercentError,
  wonToManInput,
} from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";
import { BonusRecordForm } from "./BonusRecordForm";
import { ManAmountField } from "./ManAmountField";
import {
  type EmitSource,
  type StepFormHandle,
  useDebouncedEmit,
} from "./useDebouncedEmit";
import { useFillFocus } from "./useFillFocus";
import {
  RecordSaveNote,
  type RecordSaveTracker,
  useRecordSave,
} from "./useRecordSave";
import { useRevealedErrors } from "./useRevealedErrors";

const FOCUS_SELECTORS: Record<CurrentJobFocus, string> = {
  performanceBonus: "#performanceBonusEstimate, #recordMonth",
  stock: "#stockVesting12m",
  hireDate: "#hireDate",
};

const SAVED_NOTE = "마이페이지에 저장됨";
const BONUS_AMOUNT_REQUIRED = "연간 고정상여 금액을 입력해 주세요.";

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
  /** 미반영 `넣기` 로 들어왔을 때 열고 포커스할 칸. */
  focus?: CurrentJobFocus;
  /** 유효한 입력. 처음 그릴 때 유효하면 `mount` 로 바로, 이후 편집은 400ms 뒤 `edit`. */
  onChange: (stay: Stay, source: EmitSource) => void;
  /** 상여 조건·입사일 기록 저장이 진행 중인지. 진행 중에는 호출자가 평가를 미룬다. */
  onRecordSaving: (saving: boolean) => void;
  /** 상여 조건·입사일 기록 저장이 성공한 뒤. 호출자가 맥락을 다시 읽고 재평가한다. */
  onRecordSaved: () => void;
  /** 성과급 기록을 마이페이지에 더한 뒤. 호출자가 컨텍스트(성과급 범위)를 다시 읽는다. */
  onBonusRecorded?: () => void;
}

export function CurrentJobStep({
  ref,
  context,
  latestIncomeId,
  initial,
  focus,
  onChange,
  onRecordSaving,
  onRecordSaved,
  onBonusRecorded,
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
  const { errors, setErrors, touch, revealAll, pickRevealed } =
    useRevealedErrors();
  const formRef = useRef<HTMLFormElement>(null);
  const detailsRef = useFillFocus(focus && FOCUS_SELECTORS[focus]);

  const callbacks = useRef({ onRecordSaving, onRecordSaved });
  useEffect(() => {
    callbacks.current = { onRecordSaving, onRecordSaved };
  });
  // 상여 조건·입사일 저장을 함께 센다. 모두 끝나야 평가 보류를 푼다.
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
            : context.fixedBonusAnnual,
        },
  );
  const hireDateSave = useRecordSave<string>(
    (value) => patchProfile({ hireDate: value }),
    tracker,
    null,
  );

  const range = context.performanceBonusRange;
  const overrideShown = noIncome || overrideOpen;
  // 덮어쓴 연봉은 상여까지 더한 금액이라 상여 조건을 따로 묻지도 저장하지도 않는다.
  const overrideActive = overrideShown && manInputToWon(override) !== null;
  const bonusAsked = latestIncomeId !== null && !overrideActive;

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
    if (Object.keys(found).length > 0) return null;
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
        <div className="flex flex-col gap-1 text-sm">
          <p>
            세전 연봉{" "}
            <strong>{formatKoreanSalary(context.currentAnnualGross)}</strong>
            {context.hireDate && <> · 입사일 {context.hireDate}</>}
          </p>
          {!overrideOpen && (
            <div>
              <Button
                type="button"
                variant="ghost"
                size="xs"
                onClick={() => setOverrideOpen(true)}
              >
                이번만 바꾸기
              </Button>
            </div>
          )}
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
      {overrideOpen && !noIncome && (
        <div>
          <Button
            type="button"
            variant="ghost"
            size="xs"
            onClick={() => {
              setOverrideOpen(false);
              setOverride("");
              setErrors((prev) => ({ ...prev, override: undefined }));
            }}
          >
            취소
          </Button>
        </div>
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

      {latestIncomeId && overrideActive && (
        <p className="text-sm text-muted-foreground">
          덮어쓴 연봉은 따로 받는 고정상여까지 더한 연 금액으로 계산합니다.
        </p>
      )}
      {latestIncomeId && !overrideActive && (
        <>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-medium">
              연봉 계약 금액에 상여가 들어 있나요?{" "}
              <RecordSaveNote
                status={bonusSave.status}
                target="소득 기록"
                idleNote="마이페이지 소득 기록에 저장됩니다"
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

      <details ref={detailsRef} className="rounded-lg border px-4 py-3">
        <summary className="cursor-pointer text-sm font-medium">
          성과급·주식·입사일 (선택)
        </summary>
        <div className="mt-4 flex flex-col gap-4">
          {range ? (
            <p className="text-sm">
              성과급 최근 {range.years}개년 최저 {formatKoreanSalary(range.min)}{" "}
              · 평균 {formatKoreanSalary(range.average)}
            </p>
          ) : (
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
          <ManAmountField
            id="stockVesting12m"
            label="앞으로 12개월 베스팅 주식 (만원)"
            value={vesting}
            onChange={setVesting}
          />
          <ManAmountField
            id="stockUnvested"
            label="미베스팅 잔여 주식 (만원)"
            value={unvested}
            onChange={setUnvested}
          />
          {(context.hireDate === null || hireDateSave.status !== "idle") && (
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
          )}
        </div>
      </details>
    </form>
  );
}
