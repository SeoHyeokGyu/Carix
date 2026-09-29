export interface SampleCoverage {
  sampleSize: number;
  withSalary: number | null;
  withSalaryPercent: number | null;
}

/** `salaryMissingRate` 는 비율이 아니라 퍼센트다(`DashboardMetadata`). */
export function toSampleCoverage(
  sampleSize: number | undefined,
  salaryMissingRate: number | null | undefined,
): SampleCoverage | null {
  if (sampleSize === undefined || sampleSize <= 0) return null;
  if (salaryMissingRate === null || salaryMissingRate === undefined) {
    return { sampleSize, withSalary: null, withSalaryPercent: null };
  }
  const percent = 100 - salaryMissingRate;
  return {
    sampleSize,
    withSalary: Math.round((sampleSize * percent) / 100),
    withSalaryPercent: Math.round(percent),
  };
}
