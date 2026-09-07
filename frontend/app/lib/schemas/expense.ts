import { z } from "zod";
import { values, type Option } from "./options";

/** 정본은 backend/core-api 의 ExpenseCategoryEnum 이다. */
export const EXPENSE_CATEGORIES: Option[] = [
  { value: "HOUSING", label: "주거·공공요금" },
  { value: "FOOD", label: "식비" },
  { value: "TRANSPORT", label: "교통" },
  { value: "MEDICAL", label: "의료" },
  { value: "EDUCATION", label: "교육·자기계발" },
  { value: "LEISURE", label: "여가·기타" },
];

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
