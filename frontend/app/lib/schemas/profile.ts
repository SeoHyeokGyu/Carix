import { z } from "zod";
import { values, type Option } from "./options";

/** collector 의 JobFamilyClassifier 가 만드는 값과 같아야 한다. */
export const JOB_FAMILIES: Option[] = [
  { value: "DEVELOPER", label: "개발" },
  { value: "DATA", label: "데이터" },
  { value: "AI", label: "AI/ML" },
  { value: "OTHER", label: "기타" },
];

export const EMPLOYMENT_TYPES: Option[] = [
  { value: "FULL_TIME", label: "정규직" },
  { value: "CONTRACT", label: "계약직" },
  { value: "FREELANCE", label: "프리랜서" },
  { value: "INTERN", label: "인턴" },
];

export const COMPANY_SIZES: Option[] = [
  { value: "LARGE", label: "대기업" },
  { value: "MEDIUM", label: "중견·중소" },
  { value: "STARTUP", label: "스타트업" },
];

/** collector 의 RegionRollup.SIDO 와 같은 어휘다. */
export const REGIONS: Option[] = [
  "서울",
  "부산",
  "대구",
  "인천",
  "광주",
  "대전",
  "울산",
  "세종",
  "경기",
  "강원",
  "충북",
  "충남",
  "전북",
  "전남",
  "경북",
  "경남",
  "제주",
].map((name) => ({ value: name, label: name }));

export const profileSchema = z.object({
  jobFamily: z.enum(values(JOB_FAMILIES)),
  yearsOfExperience: z.number().int().min(0).max(50),
  region: z.enum(values(REGIONS)),
  employmentType: z.enum(values(EMPLOYMENT_TYPES)),
  companySize: z.enum(values(COMPANY_SIZES)).optional(),
});

export type ProfileInput = z.infer<typeof profileSchema>;
