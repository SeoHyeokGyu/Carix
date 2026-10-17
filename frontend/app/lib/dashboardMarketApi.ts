import { coreGet } from "./coreApi";
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
  basis: "AGE_BAND_PROXY" | string;
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

export interface SalaryTrendPoint {
  yearMonth: string;
  median: number | null;
  sampleSize: number;
  disclosedSampleSize: number;
  missingRate: number;
}

export type TrendVerdict = "UP" | "DOWN" | "FLAT" | "INSUFFICIENT_SAMPLE";

export interface TrendPeriod {
  from: string;
  to: string;
  median: number | null;
  ciLow: number | null;
  ciHigh: number | null;
  disclosedSampleSize: number;
}

export interface TrendComparison {
  recent: TrendPeriod;
  previous: TrendPeriod;
  verdict: TrendVerdict;
  sources: string[];
  inProgress: string[];
}

export interface SalaryDistributionResponse {
  status: "OK" | "INSUFFICIENT_SAMPLE" | string;
  sampleSize: number;
  minRequired?: number | null;
  baseline?: WageBaseline | null;
  postingReference?: PostingReference | null;
  userPosition?: UserPosition | null;
  peerGroup?: DistributionPeerGroup | null;
  trend?: SalaryTrendPoint[] | null;
  trendComparison?: TrendComparison | null;
  careerLadder?: CareerLadder | null;
}

export interface CareerLadder {
  userBracket: string;
  basePeriod?: string;
  availableYears?: string[];
  steps: CareerLadderStep[];
}

export interface CareerLadderStep {
  bracket: string;
  median: number | null;
  raiseFromPrevious: number | null;
  source: "KOSIS" | "FALLBACK" | "UNKNOWN" | string;
}

export interface TrendPoint {
  yearMonth: string;
  purchasingPowerIndex: number | null;
  marketValueIndex: number | null;
  openingWindowIndex?: number | null;
  incomeIndex: number | null;
  personalPriceIndex: number | null;
  nominalIncome: number | null;
  realIncome: number | null;
}

export interface TrendListResponse {
  trends: TrendPoint[];
  purchasingPowerStatus: string;
  marketValueStatus: string;
  openingWindowStatus?: string;
}

export interface SnapshotRationale {
  rulesApplied: string[];
  excluded: string[];
  baseDate: string | null;
}

export interface RecordedSnapshot {
  purchasingPowerIndex: number | null;
  marketValueIndex: number | null;
  openingWindowIndex?: number | null;
  decisionSignal: string;
  label: string;
  dataVersion: string;
  ruleVersion: string;
  rationale: SnapshotRationale | null;
  recordedAt: string;
}

export interface SnapshotPoint {
  baseYearMonth: string;
  purchasingPowerIndex: number | null;
  marketValueIndex: number | null;
  openingWindowIndex?: number | null;
  decisionSignal: string;
  label: string;
  dataVersion: string;
  ruleVersion: string;
  rationale: SnapshotRationale | null;
  /** 그 달 끝(다음 달 1일 00:00 UTC 전)에 대시보드가 보여 줬을 판정. 소급 계산한 달은 null. */
  recorded: RecordedSnapshot | null;
  /** 당시 판정과 현재 규칙 값의 결정 신호가 다르면 true. */
  changed: boolean;
}

export interface SnapshotHistoryResponse {
  snapshots: SnapshotPoint[];
}

/**
 * NOTE: (cjkang) 전에는 이 모듈이 자체 `fetch` 와 `handleResponse` 로 평범한 `Error` 를
 * 던졌다. 게이트웨이 세션 검증 플래그가 켜진 뒤로 세션 없는 호출은 전부 401 인데, 그때 이
 * 경로만 로그인으로 못 보내고 문자열 오류로 접혔다. 401 처리는 `coreApi` 한 곳에 모은다.
 */

/**
 * 보유 스킬별 상세 수요 정보 조회 (S-14 스킬수요 탭)
 */
export async function fetchSkillDemands(
  baseYearMonth?: string,
): Promise<SkillDemandResponse> {
  const query = baseYearMonth
    ? `?baseYearMonth=${encodeURIComponent(baseYearMonth)}`
    : "";
  return coreGet<SkillDemandResponse>(`/v1/dashboard/skills${query}`);
}

/**
 * 동료집단 연봉 분포 조회 (S-14 연봉분포 탭)
 */
export async function fetchSalaryDistribution(
  baseYearMonth?: string,
  year?: string,
): Promise<SalaryDistributionResponse> {
  const params = new URLSearchParams();
  if (baseYearMonth) {
    params.set("baseYearMonth", baseYearMonth);
  }
  if (year) {
    params.set("year", year);
  }
  const query = params.toString() ? `?${params.toString()}` : "";
  return coreGet<SalaryDistributionResponse>(
    `/v1/dashboard/salary-distribution${query}`,
  );
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

  return coreGet<TrendListResponse>(
    `/v1/dashboard/trends?${params.toString()}`,
  );
}

/**
 * 월별 스냅샷 이력 조회 (S-11 월별 스냅샷 이력 화면)
 */
export async function fetchSnapshotHistory(): Promise<SnapshotHistoryResponse> {
  return coreGet<SnapshotHistoryResponse>("/v1/dashboard/history");
}
