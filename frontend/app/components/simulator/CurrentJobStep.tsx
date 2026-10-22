import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { updateBonusTerms } from "../../lib/api/income";
import { patchProfile } from "../../lib/api/profile";
import {
  type EvaluateRequest,
  putDraft,
  type ScenarioContext,
} from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import type { CurrentJobFocus } from "../../lib/simulatorFocus";
import {
  describeError,
  manInputToWon,
  raisePercentError,
  wonToManInput,
} from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";
import { BonusRecordForm } from "./BonusRecordForm";
import { ManAmountField } from "./ManAmountField";
import { useFillFocus } from "./useFillFocus";

const FOCUS_SELECTORS: Record<CurrentJobFocus, string> = {
  performanceBonus: "#performanceBonusEstimate, #recordMonth",
  stock: "#stockVesting12m",
  hireDate: "#hireDate",
};

const SAVED_NOTE = "마이페이지에 저장됨";

interface CurrentJobStepProps {
  context: ScenarioContext;
  /** 가장 최근 소득 행. 상여 조건은 여기부터 저장한다. 소득 이력이 없으면 null. */
  latestIncomeId: string | null;
  /** 이미 저장된 초안. 이직 입력은 그대로 이어 담는다. */
  draft: EvaluateRequest | null;
  /** 미반영 `넣기` 로 들어왔을 때 열고 포커스할 칸. */
  focus?: CurrentJobFocus;
  /** 초안 저장. 호출자가 다른 초안 저장과 순서를 맞춘다. 실패는 던진다. */
  saveDraft?: (draft: EvaluateRequest) => Promise<unknown>;
  /** 저장된 초안을 넘긴다. */
  onDone: (saved: EvaluateRequest) => void;
  /** 성과급 기록을 마이페이지에 더한 뒤. 호출자가 컨텍스트(성과급 범위)를 다시 읽는다. */
  onBonusRecorded?: () => void;
}

export function CurrentJobStep({
  context,
  latestIncomeId,
  draft,
  focus,
  saveDraft = putDraft,
  onDone,
  onBonusRecorded,
}: CurrentJobStepProps) {
  const stay = draft?.stay;
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
  const [bonusDirty, setBonusDirty] = useState(false);
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
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const detailsRef = useFillFocus(focus && FOCUS_SELECTORS[focus]);

  const range = context.performanceBonusRange;
  const overrideShown = noIncome || overrideOpen;
  // 덮어쓴 연봉은 상여까지 더한 금액이라 상여 조건을 따로 묻지도 저장하지도 않는다.
  const overrideActive = overrideShown && manInputToWon(override) !== null;

  const validate = (): FieldErrors => {
    const next: FieldErrors = {};
    const raiseError = raisePercentError(raise);
    if (raiseError) next.raisePercent = raiseError;
    if (overrideShown && manInputToWon(override) === null) {
      next.override = "세전 연봉을 입력해 주세요.";
    }
    if (!bonusIncluded && manInputToWon(bonusAmount) === null) {
      next.bonusAmount = "연간 고정상여 금액을 입력해 주세요.";
    }
    return next;
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) {
      requestAnimationFrame(() =>
        formRef.current
          ?.querySelector<HTMLElement>('[aria-invalid="true"]')
          ?.focus(),
      );
      return;
    }
    setSubmitting(true);
    let bonusSaved = false;
    try {
      if (latestIncomeId && bonusDirty && !overrideActive) {
        await updateBonusTerms({
          applyFromIncomeId: latestIncomeId,
          bonusIncluded,
          fixedBonusAnnual: bonusIncluded ? null : manInputToWon(bonusAmount),
        });
        bonusSaved = true;
      }
      if (hireDate) await patchProfile({ hireDate });
      const saved: EvaluateRequest = {
        stay: {
          raisePercent: Number(raise),
          currentGrossOverride: overrideShown ? manInputToWon(override) : null,
          performanceBonusEstimate: range ? null : manInputToWon(estimate),
          stockVesting12m: manInputToWon(vesting),
          stockUnvested: manInputToWon(unvested),
        },
        move: draft?.move ?? null,
      };
      await saveDraft(saved);
      onDone(saved);
    } catch (error) {
      const message = describeError(error);
      setErrors({
        _form: bonusSaved
          ? `상여 조건은 저장되었습니다. 그 뒤 단계에서 실패했습니다: ${message}`
          : message,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      ref={formRef}
      onSubmit={handleSubmit}
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
          onChange={setOverride}
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
            onChange={(e) => setRaise(e.target.value)}
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
              <span className="text-xs font-normal text-muted-foreground">
                {SAVED_NOTE}
              </span>
            </legend>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="bonusIncluded"
                checked={bonusIncluded}
                onChange={() => {
                  setBonusDirty(true);
                  setBonusIncluded(true);
                }}
              />
              들어 있음
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="bonusIncluded"
                checked={!bonusIncluded}
                onChange={() => {
                  setBonusDirty(true);
                  setBonusIncluded(false);
                }}
              />
              따로 받음
            </label>
          </fieldset>
          {!bonusIncluded && (
            <ManAmountField
              id="bonusAmount"
              label="연간 고정상여 (만원)"
              hint={SAVED_NOTE}
              value={bonusAmount}
              onChange={(v) => {
                setBonusDirty(true);
                setBonusAmount(v);
              }}
              error={errors.bonusAmount}
            />
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
          {context.hireDate === null && (
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
                />
              )}
            </Field>
          )}
        </div>
      </details>

      {errors._form && (
        <p role="alert" className="text-sm text-carix-danger">
          {errors._form}
        </p>
      )}

      <div>
        <Button type="submit" disabled={submitting}>
          남으면 어떻게 되는지 보기
        </Button>
      </div>
    </form>
  );
}
