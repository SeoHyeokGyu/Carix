import { z } from "zod";
import { values, type Option } from "./options";

/** 정본은 backend/core-api 의 ExpenseCategoryEnum 이다. */
export const EXPENSE_CATEGORIES = [
  { value: "HOUSING", label: "주거·공공요금" },
  { value: "FOOD", label: "식비" },
  { value: "TRANSPORT", label: "교통" },
  { value: "MEDICAL", label: "의료" },
  { value: "EDUCATION", label: "교육·자기계발" },
  { value: "LEISURE", label: "여가·기타" },
] as const satisfies Option[];

/** 미입력 카테고리는 items 에서 빼서 보낸다 — 서버가 DEFAULT_CPI 로 보간한다. */
export const expenseSchema = z.object({
  items: z.array(
    z.object({
      category: z.enum(values(EXPENSE_CATEGORIES)),
      amount: z.number().int().min(0).max(50_000_000),
    }),
  ),
});

export type ExpenseInput = z.infer<typeof expenseSchema>;

/**
 * 화면(expense.tsx)이 쓰는 카테고리 금액 오류 문구. 항목마다 다른 키
 * (`items.N.amount`)로 착지하지만 규칙은 하나뿐이라 메시지도 하나다 — 규칙
 * (위 스키마의 amount 범위)과 나란히 둔다.
 */
export const EXPENSE_ITEM_AMOUNT_MESSAGE =
  "0원 이상 5천만 원 이하의 정수로 입력해 주세요.";
