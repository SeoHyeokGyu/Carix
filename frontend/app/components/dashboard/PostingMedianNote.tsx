import type { SalaryDistributionResponse } from "../../lib/dashboardMarketApi";
import { formatManwon } from "../../lib/dataExplore";

/**
 * 판정 기준선(통계청 전 직무 중앙값) 옆에 붙이는 공고 제시 연봉 참고 줄. 판정에는 쓰지 않는다 —
 * 공개연봉 결측이 커서다. 기준선이 개발 직군 시장보다 낮게 잡힐 수 있어, 같은 직무 공고와의 차이를
 * 함께 보여 "기준선보다 높음"이 안심으로 읽히지 않게 한다. 표본이 부족하거나 연봉이 없으면 줄이 없다.
 */
export function postingMedianText(
  distribution: SalaryDistributionResponse | null,
  grossSalary: number | null,
): string | null {
  const reference = distribution?.postingReference;
  if (
    distribution?.status !== "OK" ||
    !reference ||
    reference.median === null ||
    reference.median <= 0 ||
    grossSalary === null
  ) {
    return null;
  }
  const diff = ((grossSalary - reference.median) / reference.median) * 100;
  const size = Math.abs(diff).toFixed(1);
  const position =
    size === "0.0" ? "과 같음" : `보다 ${size}% ${diff > 0 ? "높음" : "낮음"}`;
  return `공고 제시 연봉 중앙값 ${formatManwon(reference.median)}${position} (공개 ${reference.sampleSize.toLocaleString("ko-KR")}건, 참고용)`;
}

export function PostingMedianNote({
  distribution,
  grossSalary,
}: {
  distribution: SalaryDistributionResponse | null;
  grossSalary: number | null;
}) {
  const text = postingMedianText(distribution, grossSalary);
  if (!text) return null;
  return (
    <>
      <dt className="text-carix-text/70">공고 제시 연봉</dt>
      <dd className="text-carix-text/70">{text}</dd>
    </>
  );
}
