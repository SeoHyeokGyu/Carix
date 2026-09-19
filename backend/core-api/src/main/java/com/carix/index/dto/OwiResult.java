package com.carix.index.dto;

import com.carix.index.enums.IndexStatus;
import java.math.BigDecimal;

/**
 * 개방창 지수 산출 결과 (Member B → Member C).
 *
 * <p>API 규격 §5.1 이 화면에 노출하는 것은 {@code medianWindowDays}·{@code openEndedRatePercent}·{@code
 * deadlineSampleSize} 셋뿐이지만, 계약은 개발 계획 §3 의 여덟 항목을 전부 담는다 — 완화 결과는 당시 프로필에 의존해 나중에 재계산하면 같은 값이 안
 * 나오므로 그때 저장해 두지 않으면 "표본이 부족해 조건을 넓혔다"는 이력이 사라진다.
 *
 * <p><b>이상치 클램프 [25, 400] 은 이 결과가 나오기 전에 산출기 안에서 건다</b> (기능 명세 §4.5). core-api 는 받은 값을 그대로 쓴다.
 *
 * <p><b>표본 부족 형상이 고정돼 있다.</b> 마감일 보유 표본이 최소 기준 미달이면 {@code value} 는 {@code null}, {@code status} 는
 * {@link IndexStatus#INSUFFICIENT_SAMPLE}, {@code medianWindowDays} 는 {@code null} 이고 {@code
 * openEndedRatePercent}·{@code deadlineSampleSize} 는 채운다 — 둘 다 산출된 사실이다. 화면이 무엇을 그릴지는 별개다.
 *
 * <p>{@code UPDATE_DELAYED} 를 쓰지 않는다. CPI·임금통계와 달리 외부 발행 주기에 매이지 않는다 (기능 명세 §4.5).
 *
 * @param baseYearMonth 산출 대상 월. {@code YYYYMM} 6자다
 * @param value 동료집단 중앙 개방일수 ÷ 전체시장 중앙 개방일수 × 100. 계산 불가면 {@code null}
 * @param status 값이 없는 이유
 * @param peerSampleSize 동료집단 표본 수 (마감일 없는 공고 포함)
 * @param deadlineSampleSize 마감일을 가진 표본 수. 최소 표본 기준은 이 값에 건다
 * @param medianWindowDays 동료집단 중앙 개방일수
 * @param openEndedRatePercent 상시채용 비율. 단위는 퍼센트다 (0.00 ~ 100.00) — 비율이 아니다
 * @param fallbackLevel 안착한 완화 단계. MVI 완화와 독립이다
 * @param appliedConditions 화면에 그대로 쓸 안착 조건 문구
 * @param marketMedianWindowDays 기준 모수(전체시장) 중앙 개방일수. 분모다
 */
public record OwiResult(
    String baseYearMonth,
    BigDecimal value,
    IndexStatus status,
    Integer peerSampleSize,
    Integer deadlineSampleSize,
    Integer medianWindowDays,
    BigDecimal openEndedRatePercent,
    Integer fallbackLevel,
    String appliedConditions,
    Integer marketMedianWindowDays) {

  /** 입력이 없거나 산출기가 준비되지 않은 상태. */
  public static OwiResult notAvailable(String baseYearMonth) {
    return new OwiResult(
        baseYearMonth, null, IndexStatus.NOT_AVAILABLE, null, null, null, null, null, null, null);
  }
}
