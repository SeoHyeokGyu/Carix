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

export interface ApiError {
  code?: string;
  message: string;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string;
}

const JSON_HEADERS = { "Content-Type": "application/json" };

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    try {
      const errJson = (await res.json()) as Partial<ApiError>;
      if (errJson.message) {
        throw new Error(errJson.message);
      }
    } catch (e) {
      if (e instanceof Error && e.message !== "Failed to fetch") {
        throw e;
      }
    }
    throw new Error(`요청 처리에 실패했습니다. (상태 코드: ${res.status})`);
  }
  const body = (await res.json()) as ApiResponse<T>;
  return body.data;
}

/**
 * 표준 스킬 사전 검색 (자동완성)
 */
export async function searchSkills(query: string): Promise<SkillSearchItem[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];

  const res = await fetch(
    `/api/v1/skills/search?q=${encodeURIComponent(trimmed)}`,
    {
      credentials: "include",
    },
  );
  return handleResponse<SkillSearchItem[]>(res);
}

/**
 * 사용자의 보유 스킬 목록 조회
 */
export async function fetchUserSkills(): Promise<UserSkill[]> {
  const res = await fetch("/api/v1/user-skills", {
    credentials: "include",
  });
  return handleResponse<UserSkill[]>(res);
}

/**
 * 사용자 보유 스킬 등록
 */
export async function createUserSkill(
  input: CreateUserSkillInput,
): Promise<UserSkill> {
  const res = await fetch("/api/v1/user-skills", {
    method: "POST",
    headers: JSON_HEADERS,
    credentials: "include",
    body: JSON.stringify(input),
  });
  return handleResponse<UserSkill>(res);
}

/**
 * 사용자 보유 스킬 수정
 */
export async function updateUserSkill(
  id: string,
  input: UpdateUserSkillInput,
): Promise<UserSkill> {
  const res = await fetch(`/api/v1/user-skills/${id}`, {
    method: "PUT",
    headers: JSON_HEADERS,
    credentials: "include",
    body: JSON.stringify(input),
  });
  return handleResponse<UserSkill>(res);
}

/**
 * 사용자 보유 스킬 삭제
 */
export async function deleteUserSkill(id: string): Promise<void> {
  const res = await fetch(`/api/v1/user-skills/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  await handleResponse<void>(res);
}
