import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import type { EvaluateRequest } from "../../lib/api/scenario";
import type { FieldErrors } from "../../lib/fieldErrors";
import {
  manInputToWon,
  raisePercentError,
  wonToManInput,
} from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";
import { ManAmountField } from "./ManAmountField";

const EDIT_DEBOUNCE_MS = 400;
const MAX_GAP_MONTHS = 6;

type MoveInput = NonNullable<EvaluateRequest["move"]>;

interface QuickEditProps {
  draft: EvaluateRequest & { move: MoveInput };
  /** 세 칸을 고쳤을 때. 호출자가 초안 저장과 재평가를 맡는다. */
  onEdit: (next: EvaluateRequest) => void;
  onFullConditions: () => void;
  /** 입력칸이 초안과 다르거나 올바르지 않은 동안 참. */
  onDirtyChange: (dirty: boolean) => void;
}

function parseEdit(
  raise: string,
  gross: string,
  gap: string,
): { errors: FieldErrors; value: [number, number, number] | null } {
  const errors: FieldErrors = {};
  const raiseNumber = Number(raise);
  const grossWon = manInputToWon(gross);
  const gapNumber = Number(gap);
  const raiseError = raisePercentError(raise);
  if (raiseError) errors.raise = raiseError;
  if (grossWon === null) errors.gross = "이직 연봉을 입력해 주세요.";
  if (
    gap.trim() === "" ||
    !Number.isInteger(gapNumber) ||
    gapNumber < 0 ||
    gapNumber > MAX_GAP_MONTHS
  ) {
    errors.gap = `공백은 0~${MAX_GAP_MONTHS}개월로 입력해 주세요.`;
  }
  if (Object.keys(errors).length > 0) return { errors, value: null };
  return { errors, value: [raiseNumber, grossWon ?? 0, gapNumber] };
}

/** 결과 화면 상단의 핵심 입력 세 칸. 바꾸면 400ms 뒤 호출자에게 알린다. */
export function QuickEdit({
  draft,
  onEdit,
  onFullConditions,
  onDirtyChange,
}: QuickEditProps) {
  const [raise, setRaise] = useState(String(draft.stay.raisePercent));
  const [gross, setGross] = useState(wonToManInput(draft.move.grossSalary));
  const [gap, setGap] = useState(String(draft.move.unemploymentMonths));
  const latest = useRef({ draft, onEdit });
  useEffect(() => {
    latest.current = { draft, onEdit };
  });

  const { errors, value } = parseEdit(raise, gross, gap);
  const [nextRaise, nextGross, nextGap] = value ?? [];
  const dirty =
    value === null ||
    nextRaise !== draft.stay.raisePercent ||
    nextGross !== draft.move.grossSalary ||
    nextGap !== draft.move.unemploymentMonths;
  useEffect(() => {
    onDirtyChange(dirty);
  }, [dirty, onDirtyChange]);

  useEffect(() => {
    if (nextRaise === undefined) return;
    const { draft: current, onEdit: edit } = latest.current;
    if (
      nextRaise === current.stay.raisePercent &&
      nextGross === current.move.grossSalary &&
      nextGap === current.move.unemploymentMonths
    ) {
      return;
    }
    const timer = setTimeout(
      () =>
        edit({
          stay: { ...current.stay, raisePercent: nextRaise },
          move: {
            ...current.move,
            grossSalary: nextGross ?? 0,
            unemploymentMonths: nextGap ?? 0,
          },
        }),
      EDIT_DEBOUNCE_MS,
    );
    return () => clearTimeout(timer);
  }, [nextRaise, nextGross, nextGap]);

  return (
    <div className="flex flex-col gap-3 rounded-lg border px-4 py-3">
      <div className="grid gap-3 sm:grid-cols-3">
        <Field id="quickRaise" label="예상 인상률 (%)" error={errors.raise}>
          {(aria) => (
            <Input
              {...aria}
              type="text"
              inputMode="decimal"
              value={raise}
              onChange={(e) => setRaise(e.target.value)}
            />
          )}
        </Field>
        <ManAmountField
          id="quickGross"
          label="이직 연봉 (만원)"
          value={gross}
          onChange={setGross}
          error={errors.gross}
        />
        <Field id="quickGap" label="공백 (개월)" error={errors.gap}>
          {(aria) => (
            <Input
              {...aria}
              type="text"
              inputMode="numeric"
              value={gap}
              onChange={(e) => setGap(e.target.value)}
            />
          )}
        </Field>
      </div>
      <div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onFullConditions}
        >
          전체 조건
        </Button>
      </div>
    </div>
  );
}
