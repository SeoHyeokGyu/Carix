import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";

import { formatAmountInput, parseAmountInput } from "../../lib/amountInput";
import type { BonusTermsInput, IncomeResponse } from "../../lib/api/income";
import type { FieldErrors } from "../../lib/fieldErrors";
import { formatKoreanSalary } from "../../lib/koreanSalary";

const WON_PER_MAN = 10_000;
const MISSING_AMOUNT_MESSAGE = "연간 고정상여 금액을 입력해 주세요.";

interface BonusTermsFormProps {
  /** 유효 시작일 내림차순. 첫 행이 기본 적용 시작점이다. */
  incomes: readonly IncomeResponse[];
  onSubmit: (input: BonusTermsInput) => void;
  isSubmitting: boolean;
  errors?: FieldErrors;
}

function amountInputOf(income: IncomeResponse | undefined): string {
  return income?.fixedBonusAnnual == null
    ? ""
    : formatAmountInput(String(income.fixedBonusAnnual / WON_PER_MAN));
}

/**
 * 연봉 계약 금액에 상여가 들어 있는지와, 따로 받는 연간 고정상여를 기록한다.
 * 선택한 소득 행(유효 시작일)부터 적용되고, 행을 고르면 그 행의 현재 조건으로 채운다.
 */
export function BonusTermsForm({
  incomes,
  onSubmit,
  isSubmitting,
  errors,
}: BonusTermsFormProps) {
  const [applyFromId, setApplyFromId] = useState(incomes[0]?.id ?? "");
  const [bonusIncluded, setBonusIncluded] = useState(
    incomes[0]?.bonusIncluded ?? true,
  );
  const [amountInput, setAmountInput] = useState(amountInputOf(incomes[0]));
  const [localError, setLocalError] = useState<string | undefined>();

  const handleSelect = (id: string) => {
    setApplyFromId(id);
    setLocalError(undefined);
    const target = incomes.find((item) => item.id === id);
    if (!target) return;
    setBonusIncluded(target.bonusIncluded);
    setAmountInput(amountInputOf(target));
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!applyFromId) return;
    if (bonusIncluded) {
      setLocalError(undefined);
      onSubmit({
        applyFromIncomeId: applyFromId,
        bonusIncluded: true,
        fixedBonusAnnual: null,
      });
      return;
    }
    const man = parseAmountInput(amountInput);
    if (man === null) {
      setLocalError(MISSING_AMOUNT_MESSAGE);
      return;
    }
    setLocalError(undefined);
    onSubmit({
      applyFromIncomeId: applyFromId,
      bonusIncluded: false,
      fixedBonusAnnual: man * WON_PER_MAN,
    });
  };

  const options = incomes.map((item) => ({
    value: item.id,
    label: `${item.effectiveDate} (${formatKoreanSalary(item.grossSalary)})`,
  }));

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <Field id="applyFromIncomeId" label="이 연봉부터 적용">
        {(aria) => (
          <NativeSelect
            {...aria}
            name="applyFromIncomeId"
            value={applyFromId}
            onChange={(e) => handleSelect(e.target.value)}
            placeholder="연봉 이력 선택"
            options={options}
          />
        )}
      </Field>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">
          연봉 계약 금액에 상여가 들어 있나요?
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
        <Field
          id="fixedBonusAnnual"
          label="연간 고정상여 (만원)"
          hint="연봉과 별개로 매년 정해진 금액을 받는 상여의 합계입니다. (예: 1,200)"
          error={localError ?? errors?.fixedBonusAnnual}
        >
          {(aria) => (
            <Input
              {...aria}
              name="fixedBonusAnnual"
              type="text"
              inputMode="numeric"
              placeholder="예: 1,200"
              value={amountInput}
              onChange={(e) =>
                setAmountInput(formatAmountInput(e.target.value))
              }
            />
          )}
        </Field>
      )}

      {errors?._form && (
        <p role="alert" className="text-sm text-carix-danger">
          {errors._form}
        </p>
      )}

      <div>
        <Button type="submit" disabled={isSubmitting || !applyFromId}>
          상여 조건 저장
        </Button>
      </div>
    </form>
  );
}
