import type { SkillDemandStatusCode } from "./skillDemandStatus";

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
  /** 서버 어휘는 `skillDemandStatus.ts` 가 못박는다. 여기 문자열을 복제하지 않는다. */
  status: SkillDemandStatusCode;
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

export interface TrendPoint {
  yearMonth: string;
  purchasingPowerIndex: number | null;
  marketValueIndex: number | null;
  incomeIndex: number | null;
  personalPriceIndex: number | null;
  nominalIncome: number | null;
  realIncome: number | null;
}

export interface TrendListResponse {
  trends: TrendPoint[];
  purchasingPowerStatus: string;
  marketValueStatus: string;
}

export interface SnapshotRationale {
  rulesApplied: string[];
  excluded: string[];
  baseDate: string | null;
}

export interface SnapshotPoint {
  baseYearMonth: string;
  purchasingPowerIndex: number | null;
  marketValueIndex: number | null;
  decisionSignal: string;
  label: string;
  dataVersion: string;
  ruleVersion: string;
  rationale: SnapshotRationale | null;
}

export interface SnapshotHistoryResponse {
  snapshots: SnapshotPoint[];
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

/**
 * 지수 트렌드 시계열 데이터 조회 (S-14 소득 vs 물가 탭)
 */
export async function fetchTrends(
  baseYearMonth?: string,
  durationMonths = 12,
): Promise<TrendListResponse> {
  const params = new URLSearchParams();
  if (baseYearMonth) {
    params.set("baseYearMonth", baseYearMonth);
  }
  params.set("durationMonths", String(durationMonths));

  const res = await fetch(`/api/v1/dashboard/trends?${params.toString()}`, {
    credentials: "include",
  });
  return handleResponse<TrendListResponse>(res);
}

/**
 * 월별 스냅샷 이력 조회 (S-11 월별 스냅샷 이력 화면)
 */
export async function fetchSnapshotHistory(): Promise<SnapshotHistoryResponse> {
  const res = await fetch("/api/v1/dashboard/history", {
    credentials: "include",
  });
  return handleResponse<SnapshotHistoryResponse>(res);
}
