import {
  type Ref,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from "react";

import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

import {
  type EvaluateRequest,
  type Feasibility,
  getFeasibility,
} from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import type { OfferMissing } from "../../lib/simulatorCompare";
import {
  describeError,
  manInputToWon,
  wonToManInput,
} from "../../lib/simulatorForm";
import { ManAmountField } from "./ManAmountField";
import { OptionalChips, OptionalGroup } from "./OptionalItems";
import {
  type EmitSource,
  type StepFormHandle,
  useDebouncedEmit,
} from "./useDebouncedEmit";
import { useOptionalGroups } from "./useOptionalGroups";
import { useRevealedErrors } from "./useRevealedErrors";

const MAX_GAP_MONTHS = 6;
const BENEFIT_NOTE = "본인 의사로 그만두면 대개 받을 수 없습니다.";

type OptionalKey = "performanceBonus" | "signingBonus" | "stockVesting";

/** 오퍼 카드 안이라 "새 회사" 는 붙이지 않는다. */
const OPTIONAL_LABELS: Record<OptionalKey, string> = {
  performanceBonus: "성과급",
  signingBonus: "사이닝 보너스",
  stockVesting: "주식",
};
const OPTIONAL_ORDER: readonly OptionalKey[] = [
  "performanceBonus",
  "signingBonus",
  "stockVesting",
];

type Move = NonNullable<EvaluateRequest["move"]>;

/** 모든 칸이 비어 있다 — 오퍼가 없다는 뜻이지 입력 오류가 아니다. */
export const BLANK_OFFER = "blank" as const;
export type OfferEmit = Move | typeof BLANK_OFFER;

/** 입력칸 문자열 묶음. */
interface MoveFormValues {
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

function isBlankOffer(values: MoveFormValues): boolean {
  return (
    [
      values.gross,
      values.oneTimeCost,
      values.fixedBonus,
      values.performanceBonus,
      values.signingBonus,
      values.stockVesting,
    ].every((v) => v.trim() === "") &&
    values.benefitEligible === null &&
    (values.gapMonths.trim() === "" || values.gapMonths.trim() === "0")
  );
}

function missingOf(found: FieldErrors): OfferMissing {
  if (found.gross) return "gross";
  const keys = Object.keys(found);
  return keys.length === 1 && keys[0] === "benefitEligible"
    ? "benefit"
    : "other";
}

function fromDraft(move: Move): MoveFormValues {
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
  ref?: Ref<StepFormHandle<OfferEmit>>;
  /** 이미 있는 계산 요청. 처음 그릴 때 `move` 만 읽는다. */
  initial: EvaluateRequest | null;
  /** 유효한 입력. 처음 그릴 때 유효하면 `mount` 로 바로, 이후 편집은 400ms 뒤 `edit`. */
  onChange: (offer: OfferEmit, source: EmitSource) => void;
  /** 편집·flush 결과가 무효일 때. 무엇이 빠졌는지 함께 알린다. 계산 요청은 직전 값으로 남고, 호출자가 결과를 흐리게 한다. */
  onInvalid?: (missing: OfferMissing) => void;
}

export function MoveConditionsStep({
  ref,
  initial,
  onChange,
  onInvalid,
}: MoveConditionsStepProps) {
  const [values, setValues] = useState<MoveFormValues>(
    initial?.move ? fromDraft(initial.move) : EMPTY_VALUES,
  );
  const { errors, setErrors, touch, revealAll, pickRevealed } =
    useRevealedErrors();
  const [feasibility, setFeasibility] = useState<{
    text: string;
    isError: boolean;
  } | null>(null);
  const feasibilityRun = useRef(0);
  const formRef = useRef<HTMLFormElement>(null);
  // 값이 있는 항목은 늘 보인다 — 숨긴 값이 계산에 들어가지 않게.
  const optional = useOptionalGroups<OptionalKey>(
    OPTIONAL_ORDER.filter((key) => values[key].trim() !== ""),
  );

  const set = <K extends keyof MoveFormValues>(
    key: K,
    value: MoveFormValues[K],
  ) => {
    touch(key);
    setValues((prev) => ({ ...prev, [key]: value }));
  };

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
        setFeasibility({ text: feasibilityLine(result), isError: false });
      }
    } catch (error) {
      if (run === feasibilityRun.current) {
        setFeasibility({
          text: `오퍼 연봉 수준을 조회하지 못했습니다. ${describeError(error)}`,
          isError: true,
        });
      }
    }
  };

  // 저장분·템플릿으로 채워져 마운트되면 칸을 벗어날 일이 없어 연봉 수준 줄이 안 나온다 — 처음 한 번 조회한다.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 조회 결과는 비동기로 갱신한다
    void checkFeasibility();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 마운트 때 한 번만
  }, []);

  const validate = (): FieldErrors => {
    const next: FieldErrors = {};
    const gross = manInputToWon(values.gross);
    if (gross === null) {
      next.gross = "새 회사 연봉을 입력해 주세요.";
    }
    const gap = Number(values.gapMonths);
    if (!Number.isInteger(gap) || gap < 0 || gap > MAX_GAP_MONTHS) {
      next.gapMonths = `쉬는 기간은 0~${MAX_GAP_MONTHS}개월로 입력해 주세요.`;
    }
    if (values.benefitEligible === null) {
      next.benefitEligible = "실업급여 여부를 선택해 주세요.";
    }
    if (!values.bonusIncluded && manInputToWon(values.fixedBonus) === null) {
      next.fixedBonus = "연간 고정상여 금액을 입력해 주세요.";
    }
    return next;
  };

  const compute = (): OfferEmit | null => {
    if (isBlankOffer(values)) {
      setErrors({});
      return BLANK_OFFER;
    }
    const found = validate();
    setErrors(pickRevealed(found));
    if (Object.keys(found).length > 0) {
      onInvalid?.(missingOf(found));
      return null;
    }
    return {
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
    };
  };

  const debounced = useDebouncedEmit(compute, [values], onChange);

  const flush = (): OfferEmit | null => {
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
      <div className="flex flex-col gap-2">
        <div onBlur={checkFeasibility}>
          <ManAmountField
            id="gross"
            label="새 회사 연봉 (세전, 만원)"
            value={values.gross}
            onChange={(v) => set("gross", v)}
            error={errors.gross}
          />
        </div>
        {feasibility && (
          <p
            role={feasibility.isError ? "alert" : undefined}
            className={
              feasibility.isError
                ? "text-sm text-carix-danger"
                : "text-sm text-muted-foreground"
            }
          >
            {feasibility.text}
          </p>
        )}
      </div>

      {/* 계산에 바로 들어가는 질문이라 접지 않는다 — 지금 회사 카드도 같은 질문을 늘 보인다. */}
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">
          새 회사 연봉에 고정상여가 들어 있나요?
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
            onChange={() => {
              // 금액 칸이 비어 무효가 되면 결과가 "입력을 고치면" 이라 말하므로 칸 오류도 함께 보인다.
              touch("fixedBonus");
              set("bonusIncluded", false);
            }}
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
      <Field
        id="gapMonths"
        label="쉬는 기간 (개월)"
        hint="퇴사 후 새 회사 출근까지입니다."
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

      <fieldset
        aria-describedby={
          errors.benefitEligible ? "benefit-note benefit-error" : "benefit-note"
        }
        className="flex flex-col gap-2"
      >
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
          <p
            id="benefit-error"
            role="alert"
            className="text-sm text-carix-danger"
          >
            {errors.benefitEligible}
          </p>
        )}
      </fieldset>

      {optional.isAdded("performanceBonus") && (
        <OptionalGroup
          label={OPTIONAL_LABELS.performanceBonus}
          groupRef={optional.register("group", "performanceBonus")}
          onRemove={() => {
            set("performanceBonus", "");
            optional.remove("performanceBonus");
          }}
        >
          <ManAmountField
            id="performanceBonus"
            label="예상 성과급 (만원)"
            value={values.performanceBonus}
            onChange={(v) => set("performanceBonus", v)}
          />
        </OptionalGroup>
      )}
      {optional.isAdded("signingBonus") && (
        <OptionalGroup
          label={OPTIONAL_LABELS.signingBonus}
          groupRef={optional.register("group", "signingBonus")}
          onRemove={() => {
            set("signingBonus", "");
            optional.remove("signingBonus");
          }}
        >
          <ManAmountField
            id="signingBonus"
            label="사이닝 보너스 (만원)"
            value={values.signingBonus}
            onChange={(v) => set("signingBonus", v)}
          />
        </OptionalGroup>
      )}
      {optional.isAdded("stockVesting") && (
        <OptionalGroup
          label={OPTIONAL_LABELS.stockVesting}
          groupRef={optional.register("group", "stockVesting")}
          onRemove={() => {
            set("stockVesting", "");
            optional.remove("stockVesting");
          }}
        >
          <ManAmountField
            id="stockVesting"
            label="1년 안에 받을 주식 (만원)"
            value={values.stockVesting}
            onChange={(v) => set("stockVesting", v)}
          />
        </OptionalGroup>
      )}
      <OptionalChips
        items={OPTIONAL_ORDER.filter((key) => !optional.isAdded(key)).map(
          (key) => ({ key, label: OPTIONAL_LABELS[key] }),
        )}
        register={optional.register}
        onAdd={optional.add}
      />
    </form>
  );
}

function feasibilityLine(f: Feasibility): string {
  if (f.status !== "OK")
    return "표본이 부족해 오퍼 연봉 수준을 판단하지 못했습니다.";
  return f.feedbackMessage ?? "";
}
