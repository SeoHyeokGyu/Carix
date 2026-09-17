import { z } from "zod";

/**
 * 오늘(로컬 KST 달력)을 `YYYY-MM-DD` 로 돌려준다. `toISOString()` 을 쓰면 UTC 로
 * 밀려 저녁 시간대에 하루 전 날짜가 나온다.
 */
function today(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** 서버 제약과 같다 — API 규격 §7.2 의 `monthsOfExperience` 1~600. */
export const userSkillSchema = z.object({
  // 서버 UserSkillCreateRequest/UserSkillUpdateRequest/UserSkill 의
  // monthsOfExperience 는 모두 자바 원시형 int 다. 정수 검증을 빼면 1.5 처럼
  // 사용자가 입력한 값과 Jackson 이 서버에 저장하는 값(1로 절삭)이 조용히
  // 달라진다 — .int() 는 그 침묵을 드러낸다.
  monthsOfExperience: z.number().int().min(1).max(600),
  lastUsedDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/)
    .refine((value) => value <= today(), {
      message: "마지막 사용일은 과거 또는 현재 날짜여야 합니다.",
    }),
});

export type UserSkillInput = z.infer<typeof userSkillSchema>;

/**
 * 화면이 쓰는 필드별 오류 문구. 규칙과 문장을 나란히 둬서 범위만 바뀌고 문구가
 * 옛날 값으로 남는 것을 막는다 — `income.ts` 의 `INCOME_FIELD_MESSAGES` 와 같은 결.
 */
export const SKILL_FIELD_MESSAGES: Record<keyof UserSkillInput, string> = {
  monthsOfExperience: "실무 사용 기간은 1개월에서 600개월 사이여야 합니다.",
  lastUsedDate: "마지막 사용일은 과거 또는 현재 날짜여야 합니다.",
};
