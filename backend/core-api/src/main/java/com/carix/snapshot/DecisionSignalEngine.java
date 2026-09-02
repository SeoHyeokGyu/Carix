package com.carix.snapshot;

import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.dto.SourceCoverage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 결정 신호 판정 매트릭스 (기술 명세 §2.2).
 *
 * <p>DB 를 모르는 순수 함수다. 저장·버전·월 범위는 {@code SnapshotRecalculationService} 가 맡는다. A·B 산출기가 스텁인 동안
 * end-to-end 로 도달하는 신호는 {@link DecisionSignalCode#INSUFFICIENT_DATA} 하나뿐이라, 나머지 신호는 이 클래스의 단위 테스트가
 * 유일한 검증 지점이다.
 */
@Component
public class DecisionSignalEngine {

  /** 결정표 버전. 규칙표를 바꿔 배포할 때 수동으로 올린다 (기술 명세 §1.3). */
  public static final String RULE_VERSION = "1.0.0";

  private static final int MIN_SAMPLE_SIZE = 10;
  private static final BigDecimal GAP_SEEK_JOB = new BigDecimal("0.15");
  private static final BigDecimal GAP_NEGOTIATE = new BigDecimal("0.05");
  private static final BigDecimal GAP_STAY_LOWER = new BigDecimal("-0.05");
  private static final BigDecimal MVI_SEEK_JOB = new BigDecimal("110");
  private static final BigDecimal MVI_NEGOTIATE = new BigDecimal("105");
  private static final BigDecimal MVI_STABLE = new BigDecimal("90");
  private static final BigDecimal PPI_UPSKILL = new BigDecimal("95");

  public DecisionOutcome evaluate(PpiResult ppi, MviResult mvi, CurrentSalary salary) {
    List<String> insufficient = insufficientDataReasons(ppi, mvi, salary);
    if (!insufficient.isEmpty()) {
      return new DecisionOutcome(DecisionSignalCode.INSUFFICIENT_DATA, insufficient, null);
    }

    BigDecimal gap = salaryGapRatio(mvi.wageBaseline().medianGrossSalary(), salary.grossSalary());
    BigDecimal mviValue = mvi.value();
    BigDecimal ppiValue = ppi.value();

    if (gap.compareTo(GAP_SEEK_JOB) >= 0 && mviValue.compareTo(MVI_SEEK_JOB) >= 0) {
      return new DecisionOutcome(
          DecisionSignalCode.SEEK_JOB, List.of("연봉 격차 15% 이상", "보유 스킬 시장수요 상승세"), gap);
    }
    if (gap.compareTo(GAP_NEGOTIATE) >= 0
        && gap.compareTo(GAP_SEEK_JOB) < 0
        && mviValue.compareTo(MVI_NEGOTIATE) >= 0) {
      return new DecisionOutcome(
          DecisionSignalCode.NEGOTIATE, List.of("연봉 격차 5~15% 구간", "스킬 가치 상승세"), gap);
    }
    if (mviValue.compareTo(MVI_STABLE) < 0 && ppiValue.compareTo(PPI_UPSKILL) < 0) {
      return new DecisionOutcome(
          DecisionSignalCode.UPSKILL, List.of("구매력 지수 95 미만", "보유 스킬 채용수요 감소세"), gap);
    }
    if (gap.compareTo(GAP_STAY_LOWER) > 0
        && gap.compareTo(GAP_NEGOTIATE) < 0
        && mviValue.compareTo(MVI_STABLE) >= 0) {
      return new DecisionOutcome(
          DecisionSignalCode.STAY, List.of("연봉이 시장 수준에 근접", "스킬 수요 안정"), gap);
    }
    return new DecisionOutcome(DecisionSignalCode.STAY, List.of("뚜렷한 보상 격차나 가치 상승 신호 없음"), gap);
  }

  /**
   * 데이터 부족 발동 조건 5개 (기술 명세 §2.2). 지수 값이 {@code null} 인데 상태가 부족을 가리키지 않는 경우도 조건 2·3 의 일반화로 함께 막는다 —
   * 값 없이 비교하면 NPE 다.
   */
  private static List<String> insufficientDataReasons(
      PpiResult ppi, MviResult mvi, CurrentSalary salary) {
    if (totalSampleCount(mvi.sources()) < MIN_SAMPLE_SIZE) {
      return List.of("동료집단 표본 수가 통계 기준치 미만");
    }
    if (mvi.value() == null) {
      return List.of("시장가치 지수 산출 불가");
    }
    if (ppi.value() == null) {
      return List.of("구매력 지수 산출 불가");
    }
    if (mvi.wageBaseline() == null) {
      return List.of("연봉 격차 기준선(임금통계) 미확보");
    }
    if (salary == null || salary.grossSalary() <= 0) {
      return List.of("현재 연봉 미확보");
    }
    return List.of();
  }

  private static int totalSampleCount(List<SourceCoverage> sources) {
    return sources.stream().mapToInt(SourceCoverage::sampleCount).sum();
  }

  /** $(중앙값 - 연봉) / 연봉$. 사용자 연봉이 기준 중앙값보다 낮을 때 양수다 (기술 명세 §2.1). */
  private static BigDecimal salaryGapRatio(long medianGrossSalary, long currentGrossSalary) {
    return BigDecimal.valueOf(medianGrossSalary - currentGrossSalary)
        .divide(BigDecimal.valueOf(currentGrossSalary), 6, RoundingMode.HALF_UP);
  }
}
