import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { createBonus } from "../../lib/api/income";
import type { FieldErrors } from "../../lib/fieldErrors";
import { describeError, manInputToWon } from "../../lib/simulatorForm";
import {
  currentYearMonth,
  formatYearMonth,
  isFutureYearMonth,
  toWireYearMonth,
} from "../../lib/yearMonth";
import { Field } from "../onboarding/Field";
import { ManAmountField } from "./ManAmountField";

/**
 * 지급월 입력을 와이어 형식(`YYYYMM`)으로 바꾼다. 월 선택기 값(`YYYY-MM`)과, 선택기가 없어 글자 칸으로 보이는
 * 브라우저(데스크톱 Safari)에서 치는 `YYYYMM` 을 둘 다 받는다. 형식이 아니거나 이번 달 뒤면 null.
 */
function toPaidMonth(input: string): string | null {
  const wire = toWireYearMonth(input.trim());
  if (!/^\d{4}(0[1-9]|1[0-2])$/.test(wire)) return null;
  return isFutureYearMonth(wire) ? null : wire;
}

interface BonusRecordFormProps {
  /** 소득 이력 저장분임을 알리는 표식. */
  savedNote: string;
  /** 기록을 마이페이지에 더한 뒤. 호출자가 컨텍스트(성과급 범위)를 다시 읽는다. */
  onRecorded?: () => void;
}

/** ① 접힌 영역의 `성과급 기록 추가`. 단계 제출과 별개로 바로 소득 이력에 저장한다. */
export function BonusRecordForm({
  savedNote,
  onRecorded,
}: BonusRecordFormProps) {
  const [month, setMonth] = useState("");
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState<string | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});

  const handleRecord = async () => {
    const won = manInputToWon(amount);
    const paidMonth = toPaidMonth(month);
    const found: FieldErrors = {};
    if (paidMonth === null) {
      found.recordMonth = "지급월을 이번 달까지로 골라 주세요. (예: 2026-03)";
    }
    if (won === null) found.recordAmount = "금액을 입력해 주세요.";
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    try {
      await createBonus({
        paidMonth: paidMonth ?? "",
        amount: won ?? 0,
      });
      setNote("성과급 기록을 마이페이지에 저장했습니다.");
      setMonth("");
      setAmount("");
      onRecorded?.();
    } catch (error) {
      setErrors({ record: describeError(error) });
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs text-muted-foreground">
        성과급 기록 추가 · {savedNote}
      </p>
      <div className="flex flex-wrap items-end gap-2">
        <Field id="recordMonth" label="지급월" error={errors.recordMonth}>
          {(aria) => (
            <Input
              {...aria}
              type="month"
              max={formatYearMonth(currentYearMonth())}
              placeholder="2026-03"
              value={month}
              onChange={(e) => setMonth(e.target.value)}
            />
          )}
        </Field>
        <ManAmountField
          id="recordAmount"
          label="금액 (만원)"
          value={amount}
          onChange={setAmount}
          error={errors.recordAmount}
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
      {note && <p className="text-sm">{note}</p>}
    </div>
  );
}
