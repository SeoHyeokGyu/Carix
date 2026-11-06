package com.carix.index.dto;

import com.carix.index.enums.IndexStatus;
import java.math.BigDecimal;

/**
 * 채용 활발도(Opening Window Index, OWI) 산출 결과 (Member B → Member C, b22).
 *
 * <p>API 규격 §5.1 이 화면에 노출하는 것은 {@code recentSharePercent}·{@code baselineSharePercent}·{@code
 * peerSampleSize} 셋뿐이지만, 계약은 개발 계획 §3 의 일곱 근거 항목을 전부 담는다 — 완화 결과는 당시 프로필에 의존해 나중에 재계산하면 같은 값이 안
 * 나오므로 그때 저장해 두지 않으면 "표본이 부족해 조건을 넓혔다"는 이력이 사라진다.
 *
 * <p><b>이상치 클램프 [25, 400] 은 이 결과가 나오기 전에 산출기 안에서 건다</b> (기능 명세 §4.5). core-api 는 받은 값을 그대로 쓴다.
 *
 * <p><b>표본 부족 형상이 고정돼 있다.</b> 최근 3개월 또는 이전 12개월 동료집단 표본이 최소 기준 미달이면 {@code value} 는 {@code null},
 * {@code status} 는 {@link IndexStatus#INSUFFICIENT_SAMPLE} 이고, 최근 3개월 표본만 기준을 넘었으면 {@code
 * recentSharePercent} 는 채우고 {@code baselineSharePercent} 는 {@code null} 로 둔다.
 *
 * <p>{@code UPDATE_DELAYED} 를 쓰지 않는다. CPI·임금통계와 달리 외부 발행 주기에 매이지 않는다 (기능 명세 §4.5).
 *
 * @param baseYearMonth 산출 대상 월. {@code YYYYMM} 6자다
 * @param value 최근 3개월 동료집단 비중 ÷ 이전 12개월 동료집단 비중 × 100. 계산 불가면 {@code null}
 * @param status 값이 없는 이유
 * @param peerSampleSize 최근 3개월 동료집단 표본 수
 * @param baselineSampleSize 이전 12개월 동료집단 표본 수
 * @param recentSharePercent 최근 3개월 전체 신규 수집 공고 중 동료집단 비중(%). 최근 창 표본 부족이면 {@code null}
 * @param baselineSharePercent 이전 12개월 전체 신규 수집 공고 중 동료집단 비중(%). 이전 창 표본 부족이면 {@code null}
 * @param fallbackLevel 안착한 완화 단계. MVI 경력 완화와 같은 사다리를 쓴다
 * @param appliedConditions 화면에 그대로 쓸 안착 조건 문구
 * @param marketSampleSize 최근 3개월 전체시장 신규 수집 공고 수 (분모 모수)
 * @param standardError 지수의 표준오차(지수 포인트). 결합 원천으로만 낸다. 지수가 없으면 {@code null}
 * @param recentFromMonth 최근 창의 첫 달. 잡코리아 집계로 보정한 유효월 기준이라 기준월보다 이를 수 있다. {@code YYYYMM}
 * @param recentToMonth 최근 창의 마지막 달. {@code YYYYMM}
 * @param windowsFullyCovered 결합 원천 전부가 최근 3개월·이전 12개월을 채웠는가. 표본 기준만 넘으면 지수는 나오므로 따로 본다. 지수가 없으면
 *     {@code null}
 * @param maxFallbackLevel 결합 원천 중 가장 넓은 완화 단계. {@code fallbackLevel} 은 대표 원천 하나의 값이다. 지수가 없으면
 *     {@code null}
 */
public record OwiResult(
    String baseYearMonth,
    BigDecimal value,
    IndexStatus status,
    Integer peerSampleSize,
    Integer baselineSampleSize,
    BigDecimal recentSharePercent,
    BigDecimal baselineSharePercent,
    Integer fallbackLevel,
    String appliedConditions,
    Integer marketSampleSize,
    BigDecimal standardError,
    String recentFromMonth,
    String recentToMonth,
    Boolean windowsFullyCovered,
    Integer maxFallbackLevel) {

  public OwiResult {
    IndexValueContract.OWI.requireArgument(baseYearMonth, value, status);
  }

  /** 입력이 없거나 산출기가 준비되지 않은 상태. */
  public static OwiResult notAvailable(String baseYearMonth) {
    return new OwiResult(
        baseYearMonth,
        null,
        IndexStatus.NOT_AVAILABLE,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
