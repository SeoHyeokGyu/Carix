import { coreGet, coreSend } from "./coreApi";

export interface SkillSearchItem {
  id: string;
  standardName: string;
  isCustom: boolean;
}

export interface UserSkill {
  id: string;
  skillId: string;
  standardName: string;
  isCustom: boolean;
  monthsOfExperience: number;
  lastUsedDate: string;
}

export interface CreateUserSkillInput {
  standardName: string;
  monthsOfExperience: number;
  lastUsedDate: string;
  category?: string;
}

export interface UpdateUserSkillInput {
  monthsOfExperience: number;
  lastUsedDate: string;
}

/**
 * NOTE: (cjkang) 전에는 이 모듈이 자체 `fetch` 와 `handleResponse` 로 평범한 `Error` 를
 * 던졌다. 게이트웨이 세션 검증 플래그를 켜면 세션 없는 호출이 전부 401 이 되는데,
 * 그때 이 경로만 로그인으로 못 보냈다. 401 처리는 `coreApi` 한 곳에 모은다.
 */
export async function searchSkills(query: string): Promise<SkillSearchItem[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];
  return coreGet<SkillSearchItem[]>(
    `/v1/skills/search?q=${encodeURIComponent(trimmed)}`,
  );
}

export const fetchUserSkills = () => coreGet<UserSkill[]>("/v1/user-skills");

export const createUserSkill = (input: CreateUserSkillInput) =>
  coreSend<UserSkill>("POST", "/v1/user-skills", input);

export const updateUserSkill = (id: string, input: UpdateUserSkillInput) =>
  coreSend<UserSkill>("PUT", `/v1/user-skills/${id}`, input);

export const deleteUserSkill = (id: string): Promise<void> =>
  coreSend<null>("DELETE", `/v1/user-skills/${id}`).then(() => undefined);

export interface SkillCoOccurrence {
  withSkill: string;
  rate: number;
}

export interface SkillRecommendationItem {
  standardName: string;
  category: string | null;
  demandRate: number;
  growthTrend: "UP" | "DOWN" | "STABLE" | string;
  coOccurrence: SkillCoOccurrence | null;
  reason: string;
}

export interface SkillRecommendationsResponse {
  peerGroup: {
    appliedConditions: string;
  };
  recommendations: SkillRecommendationItem[];
}

/**
 * 동료집단 기반 스킬 보강 추천 목록을 조회합니다 (API 규격 §5.6, US-D6).
 */
export const fetchSkillRecommendations = (limit?: number) =>
  coreGet<SkillRecommendationsResponse>(
    limit != null
      ? `/v1/skills/recommendations?limit=${limit}`
      : "/v1/skills/recommendations",
  );
