export interface SkillDemandSource {
  source: string;
  demandIndex: number;
  sampleSize: number;
}

export interface SkillDemandDetail {
  skillId: string;
  standardName: string;
  demandIndex: number;
  recentDemandRate: number;
  baselineDemandRate: number;
  sources: SkillDemandSource[];
  weight: number;
  status: string;
}

export interface SkillDemandResponse {
  skills: SkillDemandDetail[];
  coverage: string;
}

export interface WageBaseline {
  source: string;
  median: number | null;
  wageDefinition: string;
}

export interface PostingReference {
  min: number | null;
  p25: number | null;
  median: number | null;
  p75: number | null;
  max: number | null;
  sampleSize: number;
  missingRate: number;
}

export interface UserPosition {
  grossSalary: number;
  percentile: number;
}

export interface DistributionPeerGroup {
  fallbackLevel: number | null;
  appliedConditions: string;
}

export interface SalaryDistributionResponse {
  status: "OK" | "INSUFFICIENT_SAMPLE" | string;
  sampleSize: number;
  minRequired?: number | null;
  baseline?: WageBaseline | null;
  postingReference?: PostingReference | null;
  userPosition?: UserPosition | null;
  peerGroup?: DistributionPeerGroup | null;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string;
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`요청 처리에 실패했습니다. (상태 코드: ${res.status})`);
  }
  const body = (await res.json()) as ApiResponse<T>;
  return body.data;
}

/**
 * 보유 스킬별 상세 수요 정보 조회 (S-14 스킬수요 탭)
 */
export async function fetchSkillDemands(
  baseYearMonth?: string,
): Promise<SkillDemandResponse> {
  const query = baseYearMonth
    ? `?baseYearMonth=${encodeURIComponent(baseYearMonth)}`
    : "";
  const res = await fetch(`/api/v1/dashboard/skills${query}`, {
    credentials: "include",
  });
  return handleResponse<SkillDemandResponse>(res);
}

/**
 * 동료집단 연봉 분포 조회 (S-14 연봉분포 탭)
 */
export async function fetchSalaryDistribution(
  baseYearMonth?: string,
): Promise<SalaryDistributionResponse> {
  const query = baseYearMonth
    ? `?baseYearMonth=${encodeURIComponent(baseYearMonth)}`
    : "";
  const res = await fetch(`/api/v1/dashboard/salary-distribution${query}`, {
    credentials: "include",
  });
  return handleResponse<SalaryDistributionResponse>(res);
}
