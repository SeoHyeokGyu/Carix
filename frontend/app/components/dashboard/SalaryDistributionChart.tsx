import type { SalaryDistributionResponse } from "../../lib/dashboardMarketApi";

export interface SalaryDistributionChartProps {
  data: SalaryDistributionResponse;
}

/**
 * 원 단위 금액을 "X,XXX만 원" 또는 "X억 X,XXX만 원" 형태로 포맷
 */
export function formatKoreanSalary(won: number | null | undefined): string {
  if (won == null) return "-";
  if (won === 0) return "0원";

  const eok = Math.floor(won / 100_000_000);
  const man = Math.floor((won % 100_000_000) / 10_000);

  if (eok > 0 && man > 0) {
    return `${eok}억 ${man.toLocaleString()}만 원`;
  } else if (eok > 0) {
    return `${eok}억 원`;
  } else if (man > 0) {
    return `${man.toLocaleString()}만 원`;
  } else {
    return `${won.toLocaleString()}원`;
  }
}

export function SalaryDistributionChart({
  data,
}: SalaryDistributionChartProps) {
  // 1. 표본 부족 열화 상태 (US-D2, US-D3)
  if (data.status === "INSUFFICIENT_SAMPLE" || data.sampleSize < 10) {
    return (
      <div
        data-testid="insufficient-sample-state"
        className="rounded-xl border border-carix-warning/30 bg-carix-warning/5 p-6 text-center shadow-xs"
      >
        <div className="inline-flex items-center gap-1.5 rounded-full bg-carix-warning/10 px-3 py-1 text-xs font-semibold text-carix-warning mb-3">
          ⚠️ 표본 부족
        </div>
        <h4 className="text-base font-bold text-carix-text">
          동료집단 표본 수가 부족하여 연봉 분포를 산출할 수 없습니다.
        </h4>
        <p className="mt-2 text-sm text-carix-text/70">
          현재 동료집단 공고 수:{" "}
          <strong className="text-carix-warning">{data.sampleSize}건</strong>{" "}
          (최소 기준: {data.minRequired ?? 10}건 이상 필요)
        </p>
        {data.peerGroup?.appliedConditions && (
          <p className="mt-3 text-xs text-carix-text/60 bg-carix-surface rounded-lg p-2 border border-carix-border inline-block shadow-xs">
            적용된 비교 조건: {data.peerGroup.appliedConditions}
          </p>
        )}
      </div>
    );
  }

  const { postingReference, baseline, userPosition, peerGroup } = data;

  const hasPostingSalaries = Boolean(
    postingReference &&
    (postingReference.sampleSize ?? 0) > 0 &&
    postingReference.min != null &&
    postingReference.max != null,
  );

  // 5수치 데이터
  const min = postingReference?.min ?? 0;
  const p25 = postingReference?.p25 ?? 0;
  const median = postingReference?.median ?? 0;
  const p75 = postingReference?.p75 ?? 0;
  const max = postingReference?.max ?? 0;
  const officialMedian = baseline?.median ?? null;

  // 전체 스케일 범위 계산 (사용자 연봉이나 공식 기준선이 min~max를 벗어날 경우 대비)
  const candidateValues: number[] = [];
  if (hasPostingSalaries) {
    candidateValues.push(min, p25, median, p75, max);
  }
  if (officialMedian) candidateValues.push(officialMedian);
  if (userPosition?.grossSalary) candidateValues.push(userPosition.grossSalary);

  const scaleMin =
    candidateValues.filter((v) => v > 0).length > 0
      ? Math.min(...candidateValues.filter((v) => v > 0))
      : 0;
  const scaleMax =
    candidateValues.filter((v) => v > 0).length > 0
      ? Math.max(...candidateValues.filter((v) => v > 0))
      : 0;
  const range = scaleMax - scaleMin > 0 ? scaleMax - scaleMin : 1;

  // 특정 금액의 백분율 위치 (0% ~ 100%)
  const getPercent = (value: number | null | undefined): number => {
    if (value == null) return 50;
    if (scaleMax <= scaleMin) return 50;
    const clamped = Math.max(scaleMin, Math.min(scaleMax, value));
    return ((clamped - scaleMin) / range) * 100;
  };

  const minPercent = getPercent(min);
  const leftP25 = getPercent(p25);
  const rightP75 = getPercent(p75);
  const widthIQR = Math.max(1, rightP75 - leftP25);
  const medianPercent = getPercent(median);
  const maxPercent = getPercent(max);
  const officialPercent = officialMedian ? getPercent(officialMedian) : null;
  const userPercent = userPosition
    ? getPercent(userPosition.grossSalary)
    : null;

  // 인디케이터 라벨이 차트 좌우 경계를 벗어나지 않도록 정렬 보정
  const getPercentStyle = (percent: number) => {
    if (percent <= 8) {
      return { left: `${percent}%`, transform: "translateX(0%)" };
    }
    if (percent >= 92) {
      return { left: `${percent}%`, transform: "translateX(-100%)" };
    }
    return { left: `${percent}%`, transform: "translateX(-50%)" };
  };

  // 백분위 상위 % 계산 (100 - percentile)
  const topPercentText = userPosition
    ? `상위 ${Math.max(1, 100 - userPosition.percentile)}% (백분위 ${userPosition.percentile}%)`
    : "미등록";

  return (
    <div
      data-testid="salary-distribution-chart"
      className="rounded-xl border border-carix-border bg-carix-surface p-6 shadow-xs"
    >
      {/* 1. 상단 타이틀 및 사용자 연봉 위치 요약 */}
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-carix-border pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="font-display text-lg font-bold text-carix-text">
              동료집단 연봉 분포
            </h3>
            {peerGroup?.fallbackLevel && peerGroup.fallbackLevel > 1 && (
              <span className="rounded bg-carix-warning/10 px-2 py-0.5 text-[11px] font-medium text-carix-warning">
                완화 {peerGroup.fallbackLevel}단계
              </span>
            )}
          </div>
          <p className="mt-1 text-xs text-carix-text/70">
            {peerGroup?.appliedConditions ?? "동료집단 기준선"}
          </p>
        </div>

        {userPosition ? (
          <div className="text-right">
            <span className="text-xs text-carix-text/60">내 연봉 위치</span>
            <div className="font-display text-lg font-bold text-carix-info">
              {topPercentText}
            </div>
            <span className="text-xs text-carix-text/70">
              현재 세전: {formatKoreanSalary(userPosition.grossSalary)}
            </span>
          </div>
        ) : (
          <div className="text-right">
            <span className="text-xs text-carix-text/60">내 연봉 위치</span>
            <div className="text-sm font-semibold text-carix-text/50">
              온보딩 연봉 미등록
            </div>
          </div>
        )}
      </div>

      {!hasPostingSalaries ? (
        <div
          data-testid="no-disclosed-salary-state"
          className="my-8 rounded-xl border border-carix-border/80 bg-carix-bg/40 p-6 text-center shadow-xs"
        >
          <div className="inline-flex items-center gap-1.5 rounded-full bg-carix-info/10 px-3 py-1 text-xs font-semibold text-carix-info mb-3">
            ℹ️ 공개 연봉 미제공
          </div>
          <h4 className="text-base font-bold text-carix-text">
            동료집단 공고 중 공개된 연봉 정보가 없습니다.
          </h4>
          <p className="mt-2 text-sm text-carix-text/70">
            총 동료집단 공고 <strong>{data.sampleSize}건</strong>이
            확인되었으나, 모두 연봉이 비공개(회사내규 또는 면접 후 결정 등)로
            등록되어 있습니다.
          </p>
          {officialMedian != null && (
            <p className="mt-3 text-xs text-carix-text/80">
              📌 하단의{" "}
              <strong>
                국가 공식 임금통계 기준선({formatKoreanSalary(officialMedian)})
              </strong>
              을 참고하실 수 있습니다.
            </p>
          )}
        </div>
      ) : (
        <>
          {/* 2. 인터랙티브 수평 Box-Plot 시각화 그래프 */}
          <div className="my-8 px-2">
            {/* 그래프 축 및 마커 컨테이너 */}
            <div className="relative h-32 w-full pt-6">
              {/* 사용자 위치 핀 (상단 플로팅 핀) */}
              {userPercent != null && (
                <div
                  className="absolute top-0 flex flex-col items-center z-10 transition-all duration-300"
                  style={getPercentStyle(userPercent)}
                >
                  <span className="rounded bg-carix-info px-2 py-0.5 text-[11px] font-bold text-white shadow whitespace-nowrap">
                    내 연봉 ({formatKoreanSalary(userPosition?.grossSalary)})
                  </span>
                  <span className="text-carix-info text-xs -mt-1">▼</span>
                </div>
              )}

              {/* 메인 레일 바 (전체 범위) */}
              <div className="absolute top-[48px] h-2 w-full rounded-full bg-carix-bg border border-carix-border/80" />

              {/* 공고 연봉 수염 선 (Min ~ Max Whisker) */}
              <div
                className="absolute top-[51px] h-0.5 bg-carix-info/50 z-[2]"
                style={{
                  left: `${minPercent}%`,
                  width: `${Math.max(0, maxPercent - minPercent)}%`,
                }}
              />

              {/* 최소값 세로 눈금 캡 */}
              <div
                className="absolute top-[45px] h-3.5 w-0.5 bg-carix-info/70 z-[2] -translate-x-1/2"
                style={{ left: `${minPercent}%` }}
                title={`최소: ${formatKoreanSalary(min)}`}
              />

              {/* 최대값 세로 눈금 캡 */}
              <div
                className="absolute top-[45px] h-3.5 w-0.5 bg-carix-info/70 z-[2] -translate-x-1/2"
                style={{ left: `${maxPercent}%` }}
                title={`최대: ${formatKoreanSalary(max)}`}
              />

              {/* 25% ~ 75% IQR (중위 50% 박스 영역) */}
              <div
                className="absolute top-[40px] h-6 rounded border border-carix-info/50 bg-carix-info/20 z-[3]"
                style={{
                  left: `${leftP25}%`,
                  width: `${widthIQR}%`,
                }}
                title={`25%: ${formatKoreanSalary(p25)} ~ 75%: ${formatKoreanSalary(p75)}`}
              />

              {/* 공고 연봉 중앙값 (Median) 표시선 */}
              <div
                className="absolute top-[36px] h-8 w-1 bg-carix-info z-[4] -translate-x-1/2 rounded-full"
                style={{ left: `${medianPercent}%` }}
                title={`공고 중앙값: ${formatKoreanSalary(median)}`}
              />

              {/* 공식 임금통계 기준선 (KOSIS Median) 표시선 (굵은 오렌지선) */}
              {officialPercent != null && (
                <div
                  className="absolute top-[32px] h-10 w-1 bg-carix-warning z-[5] -translate-x-1/2 rounded-full"
                  style={{ left: `${officialPercent}%` }}
                  title={`공식 임금통계 기준선: ${formatKoreanSalary(officialMedian)}`}
                />
              )}

              {/* 축 눈금 라벨 (하단) - 실제 퍼센트 위치에 정확하게 렌더링 */}
              <div className="absolute top-[68px] w-full text-[11px] text-carix-text/60">
                <span
                  className="absolute whitespace-nowrap font-medium text-carix-text/80"
                  style={getPercentStyle(minPercent)}
                >
                  {formatKoreanSalary(min)} (최소)
                </span>
                <span
                  className="absolute whitespace-nowrap font-bold text-carix-info"
                  style={getPercentStyle(medianPercent)}
                >
                  {formatKoreanSalary(median)} (공고중앙)
                </span>
                <span
                  className="absolute whitespace-nowrap font-medium text-carix-text/80"
                  style={getPercentStyle(maxPercent)}
                >
                  {formatKoreanSalary(max)} (최대)
                </span>
              </div>
            </div>

            {/* 범례 표시 */}
            <div className="mt-4 flex flex-wrap items-center justify-center gap-4 text-xs text-carix-text/80">
              <div className="flex items-center gap-1.5">
                <span className="inline-block h-3.5 w-3.5 rounded bg-carix-info/30 border border-carix-info/60" />
                <span>중위 50% 구간 (25% ~ 75%)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="inline-block h-4 w-1 bg-carix-warning" />
                <span className="font-semibold text-carix-warning">
                  공식 임금통계 기준선 ({formatKoreanSalary(officialMedian)})
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="inline-block h-4 w-0.5 bg-carix-info" />
                <span>공고 중앙값 ({formatKoreanSalary(median)})</span>
              </div>
              {userPercent != null && (
                <div className="flex items-center gap-1.5">
                  <span className="text-carix-info text-sm">▼</span>
                  <span className="font-semibold text-carix-info">내 위치</span>
                </div>
              )}
            </div>
          </div>

          {/* 3. 5수치 상세 카드 그리드 */}
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-5 text-center text-xs">
            <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
              <span className="text-carix-text/60">최소</span>
              <div className="mt-1 font-semibold text-carix-text">
                {formatKoreanSalary(min)}
              </div>
            </div>
            <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
              <span className="text-carix-text/60">하위 25%</span>
              <div className="mt-1 font-semibold text-carix-text">
                {formatKoreanSalary(p25)}
              </div>
            </div>
            <div className="rounded-lg bg-carix-info/10 p-2.5 border border-carix-info/30">
              <span className="font-medium text-carix-info">
                공고 중앙값 (50%)
              </span>
              <div className="mt-1 font-bold text-carix-info">
                {formatKoreanSalary(median)}
              </div>
            </div>
            <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
              <span className="text-carix-text/60">상위 25% (75%)</span>
              <div className="mt-1 font-semibold text-carix-text">
                {formatKoreanSalary(p75)}
              </div>
            </div>
            <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
              <span className="text-carix-text/60">최대</span>
              <div className="mt-1 font-semibold text-carix-text">
                {formatKoreanSalary(max)}
              </div>
            </div>
          </div>
        </>
      )}

      {/* 4. 하단 각주 및 데이터 신뢰도 메타데이터 (US-D3) */}
      <div className="mt-6 rounded-lg bg-carix-bg/40 p-3.5 text-xs text-carix-text/70 border border-carix-border/60 space-y-1.5">
        <p className="flex items-center gap-1.5">
          <span className="font-semibold text-carix-text">📌 판정 기준선:</span>
          <span>
            {baseline?.source ?? "공식통계"} (
            {baseline?.wageDefinition ?? "임금통계"}) — 중앙값{" "}
            {formatKoreanSalary(officialMedian)}
          </span>
        </p>
        <p className="flex items-center gap-1.5">
          <span className="font-semibold text-carix-text">
            📊 공고 표본 통계:
          </span>
          <span>
            총 동료집단 {data.sampleSize}건 중 공개연봉{" "}
            {postingReference?.sampleSize ?? 0}건 유효 (공개연봉 미공개 결측률:{" "}
            <strong>{postingReference?.missingRate ?? 0}%</strong>)
          </span>
        </p>
        <p className="text-[11px] text-carix-text/50">
          * 채용공고의 연봉은 미공개 결측률이 높아 참고 범위로만 병기되며, 핵심
          결정 신호 판정은 국가 공식 임금통계 중앙값을 기준으로 이루어집니다.
        </p>
      </div>
    </div>
  );
}
