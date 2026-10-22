import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { createBonus } from "../../lib/api/income";
import type { FieldErrors } from "../../lib/fieldErrors";
import { describeError, manInputToWon } from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";
import { ManAmountField } from "./ManAmountField";

function isPastOrCurrentMonth(yyyymm: string): boolean {
  if (!/^\d{4}(0[1-9]|1[0-2])$/.test(yyyymm)) return false;
  const now = new Date();
  const current = now.getFullYear() * 100 + now.getMonth() + 1;
  return Number(yyyymm) <= current;
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
    const found: FieldErrors = {};
    if (!isPastOrCurrentMonth(month)) {
      found.recordMonth =
        "지급 월을 yyyyMM 형식(01~12월, 이번 달까지)으로 입력해 주세요.";
    }
    if (won === null) found.recordAmount = "금액을 입력해 주세요.";
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    try {
      await createBonus({ paidMonth: month, amount: won ?? 0 });
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
        <Field
          id="recordMonth"
          label="지급 월 (yyyyMM)"
          error={errors.recordMonth}
        >
          {(aria) => (
            <Input
              {...aria}
              value={month}
              inputMode="numeric"
              placeholder="202603"
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
