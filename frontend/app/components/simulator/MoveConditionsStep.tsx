import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import {
  type EvaluateRequest,
  type Feasibility,
  getFeasibility,
  putDraft,
} from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import {
  describeError,
  manInputToWon,
  wonToManInput,
} from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";
import { ManAmountField } from "./ManAmountField";

const MAX_GAP_MONTHS = 6;
const BENEFIT_NOTE = "본인 의사로 그만두면 대개 받을 수 없습니다.";

/** 입력칸 문자열 묶음. 이전 단계로 갔다 와도 유지하도록 호출자가 들고 있을 수 있다. */
export interface MoveFormValues {
  gross: string;
  gapMonths: string;
  oneTimeCost: string;
  /** 미선택은 null — 필수 질문이라 기본값을 두지 않는다. */
  benefitEligible: boolean | null;
  bonusIncluded: boolean;
  fixedBonus: string;
  performanceBonus: string;
  signingBonus: string;
  stockVesting: string;
}

const EMPTY_VALUES: MoveFormValues = {
  gross: "",
  gapMonths: "0",
  oneTimeCost: "",
  benefitEligible: null,
  bonusIncluded: true,
  fixedBonus: "",
  performanceBonus: "",
  signingBonus: "",
  stockVesting: "",
};

function fromDraft(move: NonNullable<EvaluateRequest["move"]>): MoveFormValues {
  return {
    gross: wonToManInput(move.grossSalary),
    gapMonths: String(move.unemploymentMonths),
    oneTimeCost: wonToManInput(move.oneTimeCost),
    benefitEligible: move.benefitEligible,
    bonusIncluded: move.bonusIncluded,
    fixedBonus: wonToManInput(move.fixedBonusAnnual),
    performanceBonus: wonToManInput(move.performanceBonusExpected),
    signingBonus: wonToManInput(move.signingBonus),
    stockVesting: wonToManInput(move.stockVesting12m),
  };
}

interface MoveConditionsStepProps {
  /** ① 에서 저장된 초안. 잔류 입력을 그대로 이어 담는다. */
  draft: EvaluateRequest;
  initialValues?: MoveFormValues;
  /** 입력을 지우지 않고 ② 로 돌아간다. 호출자가 값을 들고 있다가 `initialValues` 로 돌려준다. */
  onBack: (values: MoveFormValues) => void;
  onDone: () => void;
}

export function MoveConditionsStep({
  draft,
  initialValues,
  onBack,
  onDone,
}: MoveConditionsStepProps) {
  const [values, setValues] = useState<MoveFormValues>(
    initialValues ?? (draft.move ? fromDraft(draft.move) : EMPTY_VALUES),
  );
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [feasibility, setFeasibility] = useState<string | null>(null);
  const feasibilityRun = useRef(0);
  const formRef = useRef<HTMLFormElement>(null);

  const set = <K extends keyof MoveFormValues>(
    key: K,
    value: MoveFormValues[K],
  ) => setValues((prev) => ({ ...prev, [key]: value }));

  const checkFeasibility = async () => {
    const gross = manInputToWon(values.gross);
    const run = ++feasibilityRun.current;
    if (gross === null || gross === 0) {
      setFeasibility(null);
      return;
    }
    try {
      const result = await getFeasibility(gross);
      if (run === feasibilityRun.current) {
        setFeasibility(feasibilityLine(result));
      }
    } catch (error) {
      if (run === feasibilityRun.current) {
        setFeasibility(
          `이직 가능성을 조회하지 못했습니다. ${describeError(error)}`,
        );
      }
    }
  };

  const validate = (): FieldErrors => {
    const next: FieldErrors = {};
    const gross = manInputToWon(values.gross);
    if (gross === null || gross === 0) {
      next.gross = "이직 연봉을 입력해 주세요.";
    }
    const gap = Number(values.gapMonths);
    if (!Number.isInteger(gap) || gap < 0 || gap > MAX_GAP_MONTHS) {
      next.gapMonths = `공백은 0~${MAX_GAP_MONTHS}개월로 입력해 주세요.`;
    }
    if (values.benefitEligible === null) {
      next.benefitEligible = "실업급여 여부를 선택해 주세요.";
    }
    if (!values.bonusIncluded && manInputToWon(values.fixedBonus) === null) {
      next.fixedBonus = "연간 고정상여 금액을 입력해 주세요.";
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
      await putDraft({
        stay: draft.stay,
        move: {
          grossSalary: manInputToWon(values.gross) ?? 0,
          unemploymentMonths: Number(values.gapMonths),
          oneTimeCost: manInputToWon(values.oneTimeCost) ?? 0,
          benefitEligible: values.benefitEligible === true,
          bonusIncluded: values.bonusIncluded,
          fixedBonusAnnual: values.bonusIncluded
            ? null
            : manInputToWon(values.fixedBonus),
          performanceBonusExpected: manInputToWon(values.performanceBonus),
          signingBonus: manInputToWon(values.signingBonus),
          stockVesting12m: manInputToWon(values.stockVesting),
        },
      });
      onDone();
    } catch (error) {
      setErrors({ _form: describeError(error) });
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
      <div className="flex flex-col gap-2">
        <div onBlur={checkFeasibility}>
          <ManAmountField
            id="gross"
            label="이직 연봉 (세전, 만원)"
            value={values.gross}
            onChange={(v) => set("gross", v)}
            error={errors.gross}
          />
        </div>
        {feasibility && (
          <p className="text-sm text-muted-foreground">{feasibility}</p>
        )}
      </div>

      <Field
        id="gapMonths"
        label="예상 공백 (개월)"
        hint="퇴사 후 새 직장 출근까지 쉬는 기간입니다."
        error={errors.gapMonths}
      >
        {(aria) => (
          <Input
            {...aria}
            type="text"
            inputMode="numeric"
            value={values.gapMonths}
            onChange={(e) => set("gapMonths", e.target.value)}
          />
        )}
      </Field>

      <ManAmountField
        id="oneTimeCost"
        label="일회성 비용 (만원)"
        hint="이사·위약금 등 이직 때 한 번 드는 비용입니다."
        value={values.oneTimeCost}
        onChange={(v) => set("oneTimeCost", v)}
      />

      <fieldset aria-describedby="benefit-note" className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">
          실업급여를 받을 수 있나요?
        </legend>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="benefitEligible"
            checked={values.benefitEligible === true}
            aria-invalid={Boolean(errors.benefitEligible)}
            onChange={() => set("benefitEligible", true)}
          />
          받음
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="benefitEligible"
            checked={values.benefitEligible === false}
            aria-invalid={Boolean(errors.benefitEligible)}
            onChange={() => set("benefitEligible", false)}
          />
          못 받음
        </label>
        <p id="benefit-note" className="text-xs text-muted-foreground">
          {BENEFIT_NOTE}
        </p>
        {errors.benefitEligible && (
          <p role="alert" className="text-sm text-carix-danger">
            {errors.benefitEligible}
          </p>
        )}
      </fieldset>

      <details className="rounded-lg border px-4 py-3">
        <summary className="cursor-pointer text-sm font-medium">
          상여·성과급·사이닝·주식 (선택)
        </summary>
        <div className="mt-4 flex flex-col gap-4">
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-medium">
              이직 연봉에 상여가 들어 있나요?
            </legend>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="moveBonusIncluded"
                checked={values.bonusIncluded}
                onChange={() => set("bonusIncluded", true)}
              />
              들어 있음
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="moveBonusIncluded"
                checked={!values.bonusIncluded}
                onChange={() => set("bonusIncluded", false)}
              />
              따로 받음
            </label>
          </fieldset>
          {!values.bonusIncluded && (
            <ManAmountField
              id="fixedBonus"
              label="연간 고정상여 (만원)"
              value={values.fixedBonus}
              onChange={(v) => set("fixedBonus", v)}
              error={errors.fixedBonus}
            />
          )}
          <ManAmountField
            id="performanceBonus"
            label="예상 성과급 (만원)"
            value={values.performanceBonus}
            onChange={(v) => set("performanceBonus", v)}
          />
          <ManAmountField
            id="signingBonus"
            label="사이닝 보너스 (만원)"
            value={values.signingBonus}
            onChange={(v) => set("signingBonus", v)}
          />
          <ManAmountField
            id="stockVesting"
            label="앞으로 12개월 베스팅 주식 (만원)"
            value={values.stockVesting}
            onChange={(v) => set("stockVesting", v)}
          />
        </div>
      </details>

      {errors._form && (
        <p role="alert" className="text-sm text-carix-danger">
          {errors._form}
        </p>
      )}

      <div className="flex items-center gap-3">
        <Button type="button" variant="ghost" onClick={() => onBack(values)}>
          ← 이전
        </Button>
        <Button type="submit" disabled={submitting}>
          비교 결과 보기
        </Button>
      </div>
    </form>
  );
}

function feasibilityLine(f: Feasibility): string {
  if (f.status !== "OK")
    return "표본이 부족해 이직 가능성을 판단하지 못했습니다.";
  return f.feedbackMessage ?? "";
}
