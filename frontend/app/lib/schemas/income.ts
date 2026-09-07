import { z } from "zod";

/**
 * 당월 말일을 "YYYY-MM-DD" 로 돌려준다. 기능 명세 §3 은 유효 시작일 허용 범위를
 * "과거 ~ 당월" 로 정의한다 — 오늘까지가 아니라 이번 달이 끝나는 날까지다.
 * 로컬(KST) 달력 기준으로 계산해 UTC 변환으로 인한 하루 오차를 피한다.
 */
function endOfCurrentMonth(): string {
  const now = new Date();
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  const year = lastDay.getFullYear();
  const month = String(lastDay.getMonth() + 1).padStart(2, "0");
  const day = String(lastDay.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export const incomeSchema = z.object({
  grossSalary: z.number().int().min(10_000_000).max(3_000_000_000),
  // 명세는 선택이지만 서버 IncomeRequest 가 @NotNull 이라 필수로 받는다 (설계 §8-a).
  // 0 을 대신 보내지 않는다 — 미입력과 구분되지 않아 PPI 가 세전 기반 추정으로 안 떨어진다.
  netSalary: z.number().int().min(500_000).max(200_000_000),
  effectiveDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .refine((value) => value <= endOfCurrentMonth(), {
      message: "미래 날짜는 입력할 수 없습니다.",
    }),
});

export type IncomeInput = z.infer<typeof incomeSchema>;
