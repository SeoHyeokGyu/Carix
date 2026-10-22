import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { createBonus, updateBonusTerms } from "../../lib/api/income";
import { patchProfile } from "../../lib/api/profile";
import {
  type EvaluateRequest,
  putDraft,
  type ScenarioContext,
} from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import {
  describeError,
  manInputToWon,
  wonToManInput,
} from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";
import { ManAmountField } from "./ManAmountField";

export type CurrentJobFocus = "performanceBonus" | "stock" | "hireDate";

/** 미반영 코드가 가리키는 입력칸. 이 단계에서 채울 수 없는 코드는 undefined. */
export function focusTargetOf(code: string): CurrentJobFocus | undefined {
  switch (code) {
    case "PERFORMANCE_BONUS":
      return "performanceBonus";
    case "STOCK":
      return "stock";
    case "HIRE_DATE":
      return "hireDate";
    default:
      return undefined;
  }
}

const FOCUS_IDS: Record<CurrentJobFocus, string> = {
  performanceBonus: "performanceBonusEstimate, #recordMonth",
  stock: "stockVesting12m",
  hireDate: "hireDate",
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
  onDone: () => void;
}

export function CurrentJobStep({
  context,
  latestIncomeId,
  draft,
  focus,
  onDone,
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
  const [recordMonth, setRecordMonth] = useState("");
  const [recordAmount, setRecordAmount] = useState("");
  const [recordNote, setRecordNote] = useState<string | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (!focus) return;
    formRef.current
      ?.querySelector<HTMLElement>(`#${FOCUS_IDS[focus]}`)
      ?.focus();
  }, [focus]);

  const bonusChanged =
    bonusIncluded !== context.bonusIncluded ||
    (!bonusIncluded && manInputToWon(bonusAmount) !== context.fixedBonusAnnual);
  const overrideShown = noIncome || overrideOpen;

  const validate = (): FieldErrors => {
    const next: FieldErrors = {};
    if (raise.trim() === "" || !Number.isFinite(Number(raise))) {
      next.raisePercent = "예상 인상률을 숫자로 입력해 주세요.";
    }
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
    try {
      if (latestIncomeId && bonusChanged) {
        await updateBonusTerms({
          applyFromIncomeId: latestIncomeId,
          bonusIncluded,
          fixedBonusAnnual: bonusIncluded ? null : manInputToWon(bonusAmount),
        });
      }
      if (hireDate) await patchProfile({ hireDate });
      await putDraft({
        stay: {
          raisePercent: Number(raise),
          currentGrossOverride: overrideShown ? manInputToWon(override) : null,
          performanceBonusEstimate: manInputToWon(estimate),
          stockVesting12m: manInputToWon(vesting),
          stockUnvested: manInputToWon(unvested),
        },
        move: draft?.move ?? null,
      });
      onDone();
    } catch (error) {
      setErrors({ _form: describeError(error) });
    } finally {
      setSubmitting(false);
    }
  };

  const handleRecord = async () => {
    const amount = manInputToWon(recordAmount);
    if (!/^\d{6}$/.test(recordMonth) || amount === null) {
      setErrors({ record: "지급 월(yyyyMM)과 금액을 입력해 주세요." });
      return;
    }
    try {
      await createBonus({ paidMonth: recordMonth, amount });
      setErrors({});
      setRecordNote("성과급 기록을 마이페이지에 저장했습니다.");
      setRecordMonth("");
      setRecordAmount("");
    } catch (error) {
      setErrors({ record: describeError(error) });
    }
  };

  const range = context.performanceBonusRange;

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
              : "이번 계산에만 쓰고 저장하지 않습니다."
          }
          value={override}
          onChange={setOverride}
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
            onChange={(e) => setRaise(e.target.value)}
          />
        )}
      </Field>

      {latestIncomeId && (
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
                onChange={() => setBonusIncluded(true)}
              />
              들어 있음
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="bonusIncluded"
                checked={!bonusIncluded}
                onChange={() => setBonusIncluded(false)}
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
              onChange={setBonusAmount}
              error={errors.bonusAmount}
            />
          )}
        </>
      )}

      <details
        open={focus !== undefined || undefined}
        className="rounded-lg border px-4 py-3"
      >
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
          <div className="flex flex-col gap-2">
            <p className="text-xs text-muted-foreground">
              성과급 기록 추가 · {SAVED_NOTE}
            </p>
            <div className="flex flex-wrap items-end gap-2">
              <Field id="recordMonth" label="지급 월 (yyyyMM)">
                {(aria) => (
                  <Input
                    {...aria}
                    value={recordMonth}
                    inputMode="numeric"
                    placeholder="202603"
                    onChange={(e) => setRecordMonth(e.target.value)}
                  />
                )}
              </Field>
              <ManAmountField
                id="recordAmount"
                label="금액 (만원)"
                value={recordAmount}
                onChange={setRecordAmount}
              />
              <Button type="button" variant="outline" onClick={handleRecord}>
                기록 추가
              </Button>
            </div>
            {errors.record && (
              <p role="alert" className="text-sm text-carix-danger">
                {errors.record}
              </p>
            )}
            {recordNote && <p className="text-sm">{recordNote}</p>}
          </div>
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
