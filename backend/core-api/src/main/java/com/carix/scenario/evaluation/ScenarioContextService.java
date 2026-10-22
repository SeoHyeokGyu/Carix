package com.carix.scenario.evaluation;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.service.PerformanceBonusService;
import com.carix.index.EffectiveIncome;
import com.carix.index.dto.WageBaseline;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.dto.ScenarioContextResponse.BonusRange;
import com.carix.scenario.inflation.InflationAssumptions;
import com.carix.snapshot.SalaryGapBucket;
import com.carix.wagestat.WageStatSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시뮬레이터 입력 기본값: 현재 소득·인상 이력·성과급 범위·물가 기준·시장 격차. 요청 시점 계산이고 저장하지 않는다. */
@Service
@RequiredArgsConstructor
public class ScenarioContextService {

  private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final int BONUS_LOOKBACK_YEARS = 3;
  private static final double PERCENT = 100.0;

  private final IncomeHistoryRepository incomeRepository;
  private final CareerProfileRepository careerProfileRepository;
  private final PerformanceBonusService performanceBonusService;
  private final InflationAssumptions inflationAssumptions;
  private final WageBaselineResolver wageBaselineResolver;
  private final Clock clock;

  @Transactional(readOnly = true)
  public ScenarioContextResponse context(UUID userId) {
    LocalDate today = LocalDate.now(clock);
    List<IncomeHistory> history =
        incomeRepository.findByUserIdOrderByEffectiveDateDesc(userId).stream()
            .filter(h -> !h.getEffectiveDate().isAfter(today))
            .toList();
    Optional<CareerProfile> profile = careerProfileRepository.findByUserId(userId);
    LocalDate hireDate = profile.map(CareerProfile::getHireDate).orElse(null);

    IncomeHistory latest = history.isEmpty() ? null : history.get(0);
    Long gross = latest == null ? null : EffectiveIncome.annualGross(latest);
    return new ScenarioContextResponse(
        gross,
        latest == null ? null : EffectiveIncome.monthlyNet(latest),
        hireDate,
        latest == null || latest.isBonusIncluded(),
        latest == null ? null : latest.getFixedBonusAnnual(),
        defaultRaisePercent(history),
        performanceBonusRange(userId, today, hireDate, history),
        inflationAssumptions.resolve(userId, YYYYMM.format(today)),
        gross == null ? null : marketGapPercent(userId, profile, gross));
  }

  /** 공식 임금통계(KOSIS) 중앙값 대비 격차(%, 세전). 상수 기준선뿐이거나 기준선이 없으면 null — 근거 없는 격차를 만들지 않는다. */
  public Double marketGapPercent(UUID userId, long gross) {
    return marketGapPercent(userId, careerProfileRepository.findByUserId(userId), gross);
  }

  private Double marketGapPercent(UUID userId, Optional<CareerProfile> profile, long gross) {
    if (gross <= 0) {
      return null;
    }
    WageBaseline baseline =
        wageBaselineResolver.resolveBaseline(profile.orElseGet(() -> new CareerProfile(userId)));
    if (baseline == null || baseline.source() != WageStatSource.KOSIS) {
      return null;
    }
    return Math.round(
            SalaryGapBucket.ratio(baseline.medianGrossSalary(), gross).doubleValue() * PERCENT * 10)
        / 10.0;
  }

  /** 최신 행의 변경 사유가 인상일 때만 (최신÷직전 − 1). 최신이 이직이면 그 점프는 인상률이 아니라 null. 직전 행의 사유는 보지 않는다. */
  private static Double defaultRaisePercent(List<IncomeHistory> history) {
    if (history.size() < 2 || history.get(0).getChangeReason() != IncomeChangeReasonEnum.RAISE) {
      return null;
    }
    IncomeHistory previous = history.get(1);
    long previousGross = EffectiveIncome.annualGross(previous);
    if (previousGross <= 0) {
      return null;
    }
    double ratio = EffectiveIncome.annualGross(history.get(0)) / (double) previousGross - 1;
    return Math.round(ratio * PERCENT * 10) / 10.0;
  }

  /**
   * 올해를 뺀 최근 3개 연도 중 입사 연도 이후 연도별 합. 기록 없는 해는 0 이다. 대상 연도가 없거나 대상 연도 안 기록이 한 건도 없으면 null — 0 범위를 내면
   * 화면이 예상치 입력을 감추고 성과급을 반영된 것으로 본다.
   */
  private BonusRange performanceBonusRange(
      UUID userId, LocalDate today, LocalDate hireDate, List<IncomeHistory> history) {
    Integer startYear = firstEligibleYear(hireDate, history);
    if (startYear == null) {
      return null;
    }
    List<PerformanceBonusResponse> bonuses = performanceBonusService.list(userId);
    int thisYear = YearMonth.from(today).getYear();
    long[] years =
        LongStream.rangeClosed(thisYear - BONUS_LOOKBACK_YEARS, thisYear - 1)
            .filter(year -> year >= startYear)
            .toArray();
    if (years.length == 0 || LongStream.of(years).noneMatch(year -> hasRecord(bonuses, year))) {
      return null;
    }
    long[] yearlySums = LongStream.of(years).map(year -> sumOfYear(bonuses, year)).toArray();
    LongSummaryStatistics stats = LongStream.of(yearlySums).summaryStatistics();
    return new BonusRange(stats.getMin(), Math.round(stats.getAverage()), yearlySums.length);
  }

  /** 입사 연도 다음 해. 입사일이 없으면 첫 소득 행 연도의 다음 해. 둘 다 없으면 null. */
  private static Integer firstEligibleYear(LocalDate hireDate, List<IncomeHistory> history) {
    if (hireDate != null) {
      return hireDate.getYear() + 1;
    }
    return history.stream()
        .map(h -> h.getEffectiveDate().getYear())
        .min(Integer::compare)
        .map(year -> year + 1)
        .orElse(null);
  }

  private static boolean hasRecord(List<PerformanceBonusResponse> bonuses, long year) {
    String prefix = Long.toString(year);
    return bonuses.stream().anyMatch(b -> b.paidMonth().startsWith(prefix));
  }

  private static long sumOfYear(List<PerformanceBonusResponse> bonuses, long year) {
    String prefix = Long.toString(year);
    return bonuses.stream()
        .filter(b -> b.paidMonth().startsWith(prefix))
        .mapToLong(PerformanceBonusResponse::amount)
        .sum();
  }
}
