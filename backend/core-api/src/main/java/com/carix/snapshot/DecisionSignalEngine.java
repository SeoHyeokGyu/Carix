package com.carix.snapshot;

import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.enums.SkillExclusionReason;
import java.math.BigDecimal;
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
  public static final String RULE_VERSION = "1.5.0";

  private static final int MIN_SAMPLE_SIZE = 10;
  private static final BigDecimal GAP_SEEK_JOB = new BigDecimal("0.15");
  private static final BigDecimal GAP_NEGOTIATE = new BigDecimal("0.05");
  private static final BigDecimal GAP_STAY_LOWER = new BigDecimal("-0.05");
  private static final BigDecimal MVI_SEEK_JOB = new BigDecimal("110");
  private static final BigDecimal MVI_NEGOTIATE = new BigDecimal("105");
  private static final BigDecimal MVI_STABLE = new BigDecimal("90");
  private static final BigDecimal PPI_UPSKILL = new BigDecimal("95");

  /**
   * 부분 판정 근거 문구. 시장가치 지수 없이 연봉 격차만으로 낸 판정임을 화면 규칙 칩에 밝힌다 — 이직 탐색·스킬 보강 우선은 시장가치가 있어야 해서 이 경로에서 나오지
   * 않는다.
   */
  public static final String MVI_NOT_REFLECTED = "시장가치 미반영";

  /**
   * 시장가치 점추정은 기준선을 넘지만 표본 잡음 폭(표준오차 × {@link #MVI_ERROR_MULTIPLIER}) 안이라 시장가치를 근거로 쓰지 않았다는 근거 문구.
   * 이때는 부분 판정과 같이 연봉 격차만으로 판정한다.
   */
  public static final String MVI_UNCERTAIN = "시장가치 불확실";

  /** 기준선과 비교할 때 시장가치에서 빼거나 더하는 표준오차 배수. 1 이면 잡음 1σ 안의 차이는 근거로 쓰지 않는다. */
  static final BigDecimal MVI_ERROR_MULTIPLIER = BigDecimal.ONE;

  public DecisionOutcome evaluate(PpiResult ppi, MviResult mvi, CurrentSalary salary) {
    List<String> insufficient = insufficientDataReasons(ppi, mvi, salary);
    if (!insufficient.isEmpty()) {
      return new DecisionOutcome(DecisionSignalCode.INSUFFICIENT_DATA, insufficient, null);
    }

    BigDecimal gap =
        SalaryGapBucket.ratio(mvi.wageBaseline().medianGrossSalary(), salary.grossSalary());
    if (mvi.value() == null) {
      return evaluateWithoutMvi(gap, MVI_NOT_REFLECTED);
    }
    BigDecimal mviValue = mvi.value();
    BigDecimal ppiValue = ppi.value();
    BigDecimal margin =
        mvi.standardError() == null
            ? BigDecimal.ZERO
            : mvi.standardError().multiply(MVI_ERROR_MULTIPLIER);
    BigDecimal mviLower = mviValue.subtract(margin);
    BigDecimal mviUpper = mviValue.add(margin);

    if (gap.compareTo(GAP_SEEK_JOB) >= 0 && mviValue.compareTo(MVI_SEEK_JOB) >= 0) {
      if (mviLower.compareTo(MVI_SEEK_JOB) < 0) {
        return evaluateWithoutMvi(gap, MVI_UNCERTAIN);
      }
      return new DecisionOutcome(
          DecisionSignalCode.SEEK_JOB, List.of("연봉 격차 15% 이상", "보유 스킬 시장수요 상승세"), gap);
    }
    if (gap.compareTo(GAP_NEGOTIATE) >= 0
        && gap.compareTo(GAP_SEEK_JOB) < 0
        && mviValue.compareTo(MVI_NEGOTIATE) >= 0) {
      if (mviLower.compareTo(MVI_NEGOTIATE) < 0) {
        return evaluateWithoutMvi(gap, MVI_UNCERTAIN);
      }
      return new DecisionOutcome(
          DecisionSignalCode.NEGOTIATE, List.of("연봉 격차 5~15% 구간", "스킬 가치 상승세"), gap);
    }
    if (mviValue.compareTo(MVI_STABLE) < 0 && ppiValue.compareTo(PPI_UPSKILL) < 0) {
      if (mviUpper.compareTo(MVI_STABLE) >= 0) {
        return evaluateWithoutMvi(gap, MVI_UNCERTAIN);
      }
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
   * 시장가치 없이 연봉 격차만으로 내는 부분 판정 (도메인 정의서 §6.2). 시장가치가 없거나({@link #MVI_NOT_REFLECTED}) 잡음 폭 안이라 근거로 못
   * 쓸 때({@link #MVI_UNCERTAIN}) 판정을 통째로 비우지 않고 격차로 말할 수 있는 것만 말한다.
   */
  private static DecisionOutcome evaluateWithoutMvi(BigDecimal gap, String mviReason) {
    if (gap.compareTo(GAP_SEEK_JOB) >= 0) {
      return new DecisionOutcome(
          DecisionSignalCode.NEGOTIATE, List.of("연봉 격차 15% 이상", mviReason), gap);
    }
    if (gap.compareTo(GAP_NEGOTIATE) >= 0) {
      return new DecisionOutcome(
          DecisionSignalCode.NEGOTIATE, List.of("연봉 격차 5~15% 구간", mviReason), gap);
    }
    if (gap.compareTo(GAP_STAY_LOWER) > 0) {
      return new DecisionOutcome(DecisionSignalCode.STAY, List.of("연봉이 시장 수준에 근접", mviReason), gap);
    }
    return new DecisionOutcome(DecisionSignalCode.STAY, List.of("뚜렷한 보상 격차 없음", mviReason), gap);
  }

  /**
   * 시장 집계가 수집되지 않은 구간이라 지수를 낼 수 없다는 규칙 문구. 화면이 입력 유도를 띄울지 판단하는 근거라 프런트가 이 문자열을 그대로 집어 본다 — 바꾸면
   * {@code frontend/app/lib/insufficientDataCause.ts} 와 어휘 계약 테스트를 함께 고쳐야 한다.
   */
  public static final String MARKET_COVERAGE_GAP = "시장 집계 미수집 구간";

  /** 제외 스킬이 있고 그 사유가 모두 집계 공백이면, MVI 가 비어 있는 원인은 사용자 입력이 아니라 수집 공백이다. */
  private static boolean isMarketCoverageGap(MviResult mvi) {
    return !mvi.excludedSkills().isEmpty()
        && mvi.excludedSkills().stream()
            .allMatch(s -> s.reason() == SkillExclusionReason.NO_RECENT_COVERAGE);
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
    // 시장가치가 비어도 수집 공백이 아니면 부분 판정으로 넘긴다(evaluateWithoutMvi). 수집 공백은 입력과 무관해 판정을 비워 두고 그 사실을 밝힌다.
    if (mvi.value() == null && isMarketCoverageGap(mvi)) {
      return List.of(MARKET_COVERAGE_GAP);
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

  /**
   * 완화에 성공한 원천의 표본만 더한다 (기술 명세 §2.2 데이터 부족 조건 1 — "완화 Level 5까지 적용한 <b>최종</b> 동료집단"). 실패한 원천도 끝까지
   * 밀어 모은 잔여 표본을 {@code sampleCount} 에 싣고 돌아오므로, 전부 더하면 어느 원천도 기준을 못 채웠는데 합계가 기준을 넘는 상태가 된다. 그러면 조건
   * 1 을 건너뛰고 조건 2 의 근거가 대신 나온다.
   */
  private static int totalSampleCount(List<SourceCoverage> sources) {
    return sources.stream()
        .filter(SourceCoverage::successful)
        .mapToInt(SourceCoverage::sampleCount)
        .sum();
  }
}
