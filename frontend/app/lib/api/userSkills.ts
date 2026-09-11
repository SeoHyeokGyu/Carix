import { coreGet } from "../coreApi";

/**
 * `GET /v1/user-skills` 응답 한 건. 이 화면(S-09)은 검토용 요약만 읽는다 — 추가·수정·삭제는
 * 스킬 입력 화면(S-08) 소관이라 여기서는 만들지 않는다.
 */
export interface UserSkillResponse {
  id: string;
  skillId: string;
  standardName: string;
  isCustom: boolean;
  monthsOfExperience: number;
  lastUsedDate: string;
}

export const listUserSkills = () =>
  coreGet<UserSkillResponse[]>("/v1/user-skills");
