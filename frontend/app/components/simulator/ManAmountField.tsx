import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

import { formatAmountInput } from "../../lib/amountInput";

interface ManAmountFieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
  error?: string;
  placeholder?: string;
}

/** 만원 단위 금액칸. 천 단위 콤마를 붙여 보여 준다. */
export function ManAmountField({
  id,
  label,
  value,
  onChange,
  hint,
  error,
  placeholder = "예: 6,000",
}: ManAmountFieldProps) {
  return (
    <Field id={id} label={label} hint={hint} error={error}>
      {(aria) => (
        <Input
          {...aria}
          name={id}
          type="text"
          inputMode="numeric"
          placeholder={placeholder}
          value={value}
          onChange={(e) => onChange(formatAmountInput(e.target.value))}
        />
      )}
    </Field>
  );
}
