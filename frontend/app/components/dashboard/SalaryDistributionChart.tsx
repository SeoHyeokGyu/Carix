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

interface BodyPartInfo {
  name: string;
  icon: string;
  label: string;
  desc: string;
  relativeY: number; // SVG 내부 높이 비례 (0: 발바닥 ~ 100: 정수리)
}

/**
 * 백분위(0~100)에 따른 신체 부위 매핑
 */
export function getBodyPartInfo(percentile: number): BodyPartInfo {
  if (percentile >= 90) {
    return {
      name: "정수리",
      icon: "👑",
      label: "최상위 정수리",
      desc: "동료집단 상위 10% 이내의 최상위 연봉 구간입니다.",
      relativeY: 93,
    };
  }
  if (percentile >= 75) {
    return {
      name: "머리",
      icon: "🧠",
      label: "상위 머리",
      desc: "동료집단 상위 25% 이내의 하이엔드 구간입니다.",
      relativeY: 84,
    };
  }
  if (percentile >= 55) {
    return {
      name: "어깨",
      icon: "👔",
      label: "든든한 어깨",
      desc: "동료집단 중앙값을 훌쩍 넘어 탄탄한 상위권에 안착해 있습니다.",
      relativeY: 72,
    };
  }
  if (percentile >= 45) {
    return {
      name: "가슴",
      icon: "🫀",
      label: "중심 가슴",
      desc: "동료집단 중심 축을 이루는 구간입니다.",
      relativeY: 58,
    };
  }
  if (percentile >= 35) {
    return {
      name: "허리",
      icon: "👖",
      label: "기준 허리",
      desc: "동료집단 중간(중앙값) 부근의 기준선 구간입니다.",
      relativeY: 46,
    };
  }
  if (percentile >= 20) {
    return {
      name: "무릎",
      icon: "🦵",
      label: "도약의 무릎",
      desc: "기본기를 다지며 상위 구간 도약을 준비하는 위치입니다.",
      relativeY: 28,
    };
  }
  return {
    name: "발목",
    icon: "👟",
    label: "첫걸음 발목",
    desc: "커리어 성장에 따라 가파른 연봉 상승 잠재력을 지닌 구간입니다.",
    relativeY: 10,
  };
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

  // 백분위 상위 % 계산 (100 - percentile)
  const topPercentText = userPosition
    ? `상위 ${Math.max(1, 100 - userPosition.percentile)}% (백분위 ${userPosition.percentile}%)`
    : "미등록";

  const userBodyPart = userPosition
    ? getBodyPartInfo(userPosition.percentile)
    : null;

  // 신체 그래픽 상의 사용자 위치 (bottom 기준 %: 5% ~ 95%)
  const userVisualY = userBodyPart?.relativeY ?? 50;

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
            <div className="font-display text-lg font-bold text-carix-positive">
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
          <p className="mt-2 text-xs text-carix-text/70">
            총 동료집단 공고{" "}
            <strong className="text-carix-text">{data.sampleSize}건</strong> 중
            급여가 공개된 공고 표본이 없어 공고 기반 5분위 연봉 통계를 산출할 수
            없습니다.
          </p>
          {officialMedian && (
            <p className="mt-2 text-xs text-carix-text/80">
              대신 하단의{" "}
              <strong>
                국가 공식 임금통계 기준선({formatKoreanSalary(officialMedian)})
              </strong>
              을 참고하실 수 있습니다.
            </p>
          )}
        </div>
      ) : (
        <>
          {/* 2. "머리-어깨-무릎-발" 신체 형태 인터랙티브 시각화 */}
          <div className="my-6 rounded-2xl bg-carix-bg/40 p-5 sm:p-6 border border-carix-border/70">
            {/* 상단 브리핑 헤드라인 */}
            {userPosition && userBodyPart && (
              <div className="mb-6 flex items-center gap-3 rounded-xl bg-carix-surface p-4 border border-carix-border shadow-2xs">
                <span className="text-3xl">{userBodyPart.icon}</span>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-sm text-carix-text sm:text-base">
                      회원님의 연봉은 현재{" "}
                      <span className="text-carix-positive underline decoration-carix-positive/40 underline-offset-4">
                        [{userBodyPart.name}]
                      </span>
                      에 위치해 있어요!
                    </span>
                    <span className="rounded-full bg-carix-positive/10 px-2 py-0.5 text-[11px] font-bold text-carix-positive border border-carix-positive/30">
                      {userBodyPart.label}
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs text-carix-text/70">
                    {userBodyPart.desc} (현재 세전:{" "}
                    <strong>
                      {formatKoreanSalary(userPosition.grossSalary)}
                    </strong>
                    , {topPercentText})
                  </p>
                </div>
              </div>
            )}

            {/* 인체 실루엣 및 신체 부위별 연봉 맵핑 영역 */}
            <div className="grid grid-cols-1 md:grid-cols-12 items-center gap-6">
              {/* 왼쪽: 세련된 인체 실루엣 그래픽 (4 컬럼) */}
              <div className="md:col-span-4 flex flex-col items-center justify-center py-2">
                <div className="relative h-64 w-32 flex items-center justify-center">
                  {/* 신체 실루엣 SVG */}
                  <svg
                    viewBox="0 0 100 240"
                    className="h-full w-full overflow-visible drop-shadow-xs"
                    aria-label="연봉 위치 인체 실루엣"
                  >
                    <defs>
                      <linearGradient
                        id="userBodyGlow"
                        x1="0%"
                        y1="100%"
                        x2="0%"
                        y2="0%"
                      >
                        <stop
                          offset={`${userVisualY}%`}
                          stopColor="var(--carix-positive)"
                          stopOpacity="0.35"
                        />
                        <stop
                          offset={`${userVisualY}%`}
                          stopColor="currentColor"
                          stopOpacity="0.08"
                        />
                      </linearGradient>
                    </defs>

                    {/* 머리 */}
                    <circle
                      cx="50"
                      cy="22"
                      r="13"
                      className="fill-current text-carix-text/25 transition-all duration-500"
                    />

                    {/* 몸통 및 두 다리 (어깨, 가슴, 허리, 좌우 다리 완벽 분리) */}
                    <path
                      d="M36 44 C36 41 42 39 50 39 C58 39 64 41 64 44 L72 50 C75 52 76 56 75 60 L71 98 C70 102 66 104 62 103 L60 102 L62 120 L64 155 C64 162 65 188 66 205 L68 215 C68 218 66 220 62 220 L55 220 C53 220 52 218 52 215 L53 205 C53 190 52 165 52 155 L51 126 C50.5 124 49.5 124 49 126 L48 155 C48 165 47 190 47 205 L48 215 C48 218 47 220 45 220 L38 220 C34 220 32 218 32 215 L34 205 C35 188 36 162 36 155 L38 120 L40 102 L38 103 C34 104 30 102 29 98 L25 60 C24 56 25 52 28 50 Z"
                      className="fill-current text-carix-text/25 transition-all duration-500"
                    />

                    {/* 주요 신체 부위 가이드 마커 점선 (SVG 내부 가이드) */}
                    {/* 정수리 (93%) */}
                    <line
                      x1="10"
                      y1="16"
                      x2="90"
                      y2="16"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                    {/* 어깨 (72%) */}
                    <line
                      x1="10"
                      y1="55"
                      x2="90"
                      y2="55"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                    {/* 허리 (46%) */}
                    <line
                      x1="10"
                      y1="115"
                      x2="90"
                      y2="115"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                    {/* 무릎 (28%) */}
                    <line
                      x1="10"
                      y1="164"
                      x2="90"
                      y2="164"
                      className="stroke-carix-text/20 stroke-1 stroke-dasharray-2"
                      strokeDasharray="3 3"
                    />
                  </svg>

                  {/* 사용자 연봉 실루엣 스캔 빔 & 핀 포인트 */}
                  {userPosition && (
                    <div
                      className="absolute left-0 right-0 z-20 flex items-center transition-all duration-500"
                      style={{ bottom: `${userVisualY}%` }}
                      title={`내 연봉: ${formatKoreanSalary(userPosition.grossSalary)}`}
                    >
                      {/* 수평 스캔 레이저 라인 */}
                      <div className="h-0.5 w-full bg-carix-positive shadow-[0_0_8px_var(--carix-positive)]" />
                      {/* 중심 핀 포인트 헤드 */}
                      <div className="absolute left-1/2 -translate-x-1/2 h-3.5 w-3.5 rounded-full bg-carix-positive ring-4 ring-white dark:ring-carix-surface shadow-md" />
                    </div>
                  )}
                </div>

                <span className="mt-2 text-[11px] text-carix-text/50 font-medium">
                  인체 높이별 연봉 백분위
                </span>
              </div>

              {/* 오른쪽: 신체 부위별 연봉 레벨 및 시장 기준선 (8 컬럼) */}
              <div className="md:col-span-8 flex flex-col justify-between space-y-2.5">
                {/* 1. 정수리 / 머리 (상위 10% ~ 25%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userPosition && userPosition.percentile >= 75
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">👑</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        정수리 · 머리
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (상위 10% ~ 25%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(p75)} 이상
                    </span>
                    {userPosition && userPosition.percentile >= 75 && (
                      <span className="ml-2 rounded bg-carix-positive px-1.5 py-0.5 text-[10px] font-bold text-white">
                        내 위치
                      </span>
                    )}
                  </div>
                </div>

                {/* 2. 어깨 (상위 25% ~ 45%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userPosition &&
                    userPosition.percentile >= 55 &&
                    userPosition.percentile < 75
                      ? "bg-carix-positive/10 border-carix-positive/50 shadow-xs ring-1 ring-carix-positive/30"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">👔</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        어깨
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (상위 25% ~ 45%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(median)} ~ {formatKoreanSalary(p75)}
                    </span>
                    {userPosition &&
                      userPosition.percentile >= 55 &&
                      userPosition.percentile < 75 && (
                        <span className="ml-2 rounded bg-carix-positive px-1.5 py-0.5 text-[10px] font-bold text-white shadow-xs">
                          내 위치 (
                          {formatKoreanSalary(userPosition.grossSalary)})
                        </span>
                      )}
                  </div>
                </div>

                {/* 3. 허리 (동료집단 중심 기준선: 공고 중앙값 & 공식 임금통계) */}
                <div
                  className={`flex flex-col gap-1.5 rounded-xl p-2.5 border transition-all ${
                    userPosition &&
                    userPosition.percentile >= 35 &&
                    userPosition.percentile < 55
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-base">👖</span>
                      <div>
                        <span className="font-bold text-xs text-carix-text">
                          허리
                        </span>
                        <span className="ml-1.5 text-[11px] text-carix-text/50">
                          (동료집단 중심선 50%)
                        </span>
                      </div>
                    </div>
                    {userPosition &&
                      userPosition.percentile >= 35 &&
                      userPosition.percentile < 55 && (
                        <span className="rounded bg-carix-positive px-1.5 py-0.5 text-[10px] font-bold text-white">
                          내 위치 (
                          {formatKoreanSalary(userPosition.grossSalary)})
                        </span>
                      )}
                  </div>

                  {/* 허리 부근의 핵심 비교 기준선 2종 */}
                  <div className="mt-1 grid grid-cols-1 sm:grid-cols-2 gap-1.5 pt-1 border-t border-carix-border/50 text-xs">
                    <div className="flex items-center justify-between rounded bg-carix-info/5 px-2 py-1 border border-carix-info/20">
                      <span className="flex items-center gap-1 text-[11px] font-medium text-carix-info">
                        <span className="inline-block h-2 w-2 rounded-full bg-carix-info" />
                        공고 중앙값
                      </span>
                      <span className="font-bold text-carix-info">
                        {formatKoreanSalary(median)}
                      </span>
                    </div>

                    {officialMedian != null && (
                      <div className="flex items-center justify-between rounded bg-carix-warning/5 px-2 py-1 border border-carix-warning/20">
                        <span className="flex items-center gap-1 text-[11px] font-medium text-carix-warning">
                          <span className="inline-block h-2 w-2 rounded-full bg-carix-warning" />
                          공식 기준선 (KOSIS)
                        </span>
                        <span className="font-bold text-carix-warning">
                          {formatKoreanSalary(officialMedian)}
                        </span>
                      </div>
                    )}
                  </div>
                </div>

                {/* 4. 무릎 (하위 25% ~ 35%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userPosition &&
                    userPosition.percentile >= 20 &&
                    userPosition.percentile < 35
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">🦵</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        무릎
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (하위 20% ~ 35%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(p25)} ~ {formatKoreanSalary(median)}
                    </span>
                    {userPosition &&
                      userPosition.percentile >= 20 &&
                      userPosition.percentile < 35 && (
                        <span className="ml-2 rounded bg-carix-positive px-1.5 py-0.5 text-[10px] font-bold text-white">
                          내 위치 (
                          {formatKoreanSalary(userPosition.grossSalary)})
                        </span>
                      )}
                  </div>
                </div>

                {/* 5. 발목 (최소 ~ 하위 20%) */}
                <div
                  className={`flex items-center justify-between rounded-xl p-2.5 border transition-all ${
                    userPosition && userPosition.percentile < 20
                      ? "bg-carix-positive/10 border-carix-positive/40 shadow-xs"
                      : "bg-carix-surface/80 border-carix-border/80"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-base">👟</span>
                    <div>
                      <span className="font-bold text-xs text-carix-text">
                        발목
                      </span>
                      <span className="ml-1.5 text-[11px] text-carix-text/50">
                        (최소 ~ 하위 20%)
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="font-semibold text-xs text-carix-text">
                      {formatKoreanSalary(min)} ~ {formatKoreanSalary(p25)}
                    </span>
                    {userPosition && userPosition.percentile < 20 && (
                      <span className="ml-2 rounded bg-carix-positive px-1.5 py-0.5 text-[10px] font-bold text-white">
                        내 위치 ({formatKoreanSalary(userPosition.grossSalary)})
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* 범례 표시 */}
            <div className="mt-5 flex flex-wrap items-center justify-center gap-4 text-xs text-carix-text/80 pt-3 border-t border-carix-border/60">
              {userPosition && (
                <div className="flex items-center gap-1.5">
                  <span className="inline-block h-3 w-3 rounded-full bg-carix-positive" />
                  <span className="font-semibold text-carix-positive">
                    내 연봉 ({formatKoreanSalary(userPosition.grossSalary)})
                  </span>
                </div>
              )}
              <div className="flex items-center gap-1.5">
                <span className="inline-block h-3 w-3 rounded-full bg-carix-info" />
                <span>공고 중앙값 ({formatKoreanSalary(median)})</span>
              </div>
              {officialMedian != null && (
                <div className="flex items-center gap-1.5">
                  <span className="inline-block h-3 w-3 rounded-full bg-carix-warning" />
                  <span className="font-semibold text-carix-warning">
                    공식 임금통계 기준선 ({formatKoreanSalary(officialMedian)})
                  </span>
                </div>
              )}
              <div className="flex items-center gap-1.5 text-carix-text/60">
                <span className="text-sm">🧍‍♂️</span>
                <span>신체 부위별 5단계 기준</span>
              </div>
            </div>
          </div>

          {/* 3. 5수치 상세 카드 그리드 (공고 표본 분위별 기준 연봉) */}
          <div className="mt-8">
            <div className="mb-2.5 flex items-center justify-between text-xs text-carix-text/70">
              <span className="font-semibold text-carix-text/90">
                📊 공고 표본 5분위 기준 연봉 (참고치)
              </span>
              <span className="text-[11px] text-carix-text/50">
                공개 표본 {postingReference?.sampleSize ?? 0}건 기준
              </span>
            </div>

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
              <div className="rounded-lg bg-carix-bg/60 p-2.5 border border-carix-border">
                <span className="text-carix-text/60">공고 중앙값 (50%)</span>
                <div className="mt-1 font-semibold text-carix-text">
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
          </div>
        </>
      )}

      {/* 4. 하단 각주 및 데이터 신뢰도 메타데이터 (US-D3) */}
      <div className="mt-6 rounded-lg bg-carix-bg/40 p-3.5 text-xs text-carix-text/70 border border-carix-border/60 space-y-1.5">
        <p className="flex items-center gap-1.5">
          <span className="font-semibold text-carix-text">📌 판정 기준선:</span>
          <span>
            {baseline?.source ?? "국가통계"} (
            {baseline?.wageDefinition ?? "공식 임금통계"}) — 중앙값{" "}
            {formatKoreanSalary(baseline?.median)}
          </span>
        </p>
        <p className="flex items-center gap-1.5">
          <span className="font-semibold text-carix-text">
            📊 공고 표본 통계:
          </span>
          <span>
            총 동료집단 {data.sampleSize}건 중 공개연봉{" "}
            {postingReference?.sampleSize ?? 0}건 유효 (공개연봉 미공개 결측률:{" "}
            <strong>{postingReference?.missingRate?.toFixed(1) ?? 0}%</strong>)
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
