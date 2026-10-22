import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import { formatAmountInput, parseAmountInput } from "../../lib/amountInput";
import type { BonusInput, BonusResponse } from "../../lib/api/income";
import type { FieldErrors } from "../../lib/fieldErrors";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import {
  currentYearMonth,
  formatYearMonth,
  toWireYearMonth,
} from "../../lib/yearMonth";
import { Field } from "../onboarding/Field";

const WON_PER_MAN = 10_000;

interface PerformanceBonusListProps {
  bonuses: readonly BonusResponse[];
  onCreate: (input: BonusInput) => void;
  onDelete: (id: string) => void;
  isSubmitting: boolean;
  errors?: FieldErrors;
}

/**
 * 받은 성과급(지급월·금액) 기록. 지급월은 미래 달을 고를 수 없다. 제출해도 입력을
 * 비우지 않는다 — 저장 실패 시 입력 보존(기능 명세 §5)을 위해, 성공 뒤 비우기는
 * 호출부가 `key` 를 바꿔 리마운트하는 것으로 맡는다.
 */
export function PerformanceBonusList({
  bonuses,
  onCreate,
  onDelete,
  isSubmitting,
  errors,
}: PerformanceBonusListProps) {
  const [monthInput, setMonthInput] = useState("");
  const [amountInput, setAmountInput] = useState("");
  const [localErrors, setLocalErrors] = useState<FieldErrors>({});

  const maxMonth = formatYearMonth(currentYearMonth());
  const shown = { ...errors, ...localErrors };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    const man = parseAmountInput(amountInput);
    const next: FieldErrors = {};
    if (!monthInput) next.paidMonth = "지급월을 선택해 주세요.";
    if (man === null) next.amount = "금액을 입력해 주세요.";
    setLocalErrors(next);
    if (!monthInput || man === null) return;
    onCreate({
      paidMonth: toWireYearMonth(monthInput),
      amount: man * WON_PER_MAN,
    });
  };

  return (
    <div className="flex flex-col gap-4">
      {bonuses.length === 0 ? (
        <EmptyState
          variant="plain"
          icon="🎁"
          title="받은 성과급이 있으면 지급월과 금액을 기록해 두세요"
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>지급월</TableHead>
              <TableHead>금액</TableHead>
              <TableHead>작업</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {bonuses.map((bonus) => (
              <TableRow key={bonus.id}>
                <TableCell>{formatYearMonth(bonus.paidMonth)}</TableCell>
                <TableCell>{formatKoreanSalary(bonus.amount)}</TableCell>
                <TableCell>
                  <ConfirmDialog
                    trigger={
                      <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        disabled={isSubmitting}
                      >
                        삭제
                      </Button>
                    }
                    title="성과급 기록을 삭제할까요?"
                    description={`${formatYearMonth(bonus.paidMonth)} 성과급 기록이 지워집니다. 되돌릴 수 없습니다.`}
                    confirmLabel="삭제"
                    onConfirm={() => onDelete(bonus.id)}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Field id="bonusPaidMonth" label="지급월" error={shown.paidMonth}>
          {(aria) => (
            <Input
              {...aria}
              name="paidMonth"
              type="month"
              max={maxMonth}
              value={monthInput}
              onChange={(e) => setMonthInput(e.target.value)}
            />
          )}
        </Field>
        <Field id="bonusAmount" label="금액 (만원)" error={shown.amount}>
          {(aria) => (
            <Input
              {...aria}
              name="amount"
              type="text"
              inputMode="numeric"
              placeholder="예: 500"
              value={amountInput}
              onChange={(e) =>
                setAmountInput(formatAmountInput(e.target.value))
              }
            />
          )}
        </Field>
        {shown._form && (
          <p role="alert" className="text-sm text-carix-danger">
            {shown._form}
          </p>
        )}
        <div>
          <Button type="submit" disabled={isSubmitting}>
            성과급 추가
          </Button>
        </div>
      </form>
    </div>
  );
}
