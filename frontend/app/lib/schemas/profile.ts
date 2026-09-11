import { z } from "zod";
import { values, type Option } from "./options";

/**
 * collector 의 JobFamilyClassifier 가 만드는 값과 같아야 한다.
 *
 * `: Option[]` 대신 `as const satisfies Option[]` 로 선언한다 — 타입을
 * 넓히지 않으면서 형태만 `Option[]` 인지 검사해, `values()` 제네릭이
 * `value` 리터럴 유니온을 그대로 추론하게 한다.
 */
export const JOB_FAMILIES = [
  { value: "DEVELOPER", label: "개발" },
  { value: "DATA", label: "데이터" },
  { value: "AI", label: "AI/ML" },
  { value: "OTHER", label: "기타" },
] as const satisfies Option[];

export const EMPLOYMENT_TYPES = [
  { value: "FULL_TIME", label: "정규직" },
  { value: "CONTRACT", label: "계약직" },
  { value: "FREELANCE", label: "프리랜서" },
  { value: "INTERN", label: "인턴" },
] as const satisfies Option[];

export const COMPANY_SIZES = [
  { value: "LARGE", label: "대기업" },
  { value: "MEDIUM", label: "중견·중소" },
  { value: "STARTUP", label: "스타트업" },
] as const satisfies Option[];

/**
 * collector 의 RegionRollup.SIDO 와 같은 어휘다. 리터럴을 살리려면 `.map()`
 * 으로 생성할 수 없다 — 배열 리터럴로 직접 나열한다.
 */
export const REGIONS = [
  { value: "서울", label: "서울" },
  { value: "부산", label: "부산" },
  { value: "대구", label: "대구" },
  { value: "인천", label: "인천" },
  { value: "광주", label: "광주" },
  { value: "대전", label: "대전" },
  { value: "울산", label: "울산" },
  { value: "세종", label: "세종" },
  { value: "경기", label: "경기" },
  { value: "강원", label: "강원" },
  { value: "충북", label: "충북" },
  { value: "충남", label: "충남" },
  { value: "전북", label: "전북" },
  { value: "전남", label: "전남" },
  { value: "경북", label: "경북" },
  { value: "경남", label: "경남" },
  { value: "제주", label: "제주" },
] as const satisfies Option[];

export const profileSchema = z.object({
  jobFamily: z.enum(values(JOB_FAMILIES)),
  yearsOfExperience: z.number().int().min(0).max(50),
  region: z.enum(values(REGIONS)),
  employmentType: z.enum(values(EMPLOYMENT_TYPES)),
  companySize: z.enum(values(COMPANY_SIZES)).optional(),
});

export type ProfileInput = z.infer<typeof profileSchema>;

/**
 * 화면(profile.tsx)이 쓰는 필드별 오류 문구. 규칙(위 스키마)과 그 규칙을 사람이
 * 읽을 문장으로 바로 옆에 두어, 범위가 바뀌었는데 문구만 옛날 값으로 남는 것을
 * 막는다.
 */
export const PROFILE_FIELD_MESSAGES: Record<keyof ProfileInput, string> = {
  jobFamily: "주 직무를 선택해 주세요.",
  yearsOfExperience: "총 경력은 0~50 사이의 정수로 입력해 주세요.",
  region: "희망 근무 지역을 선택해 주세요.",
  employmentType: "고용 형태를 선택해 주세요.",
  companySize: "회사 규모를 올바르게 선택해 주세요.",
};
