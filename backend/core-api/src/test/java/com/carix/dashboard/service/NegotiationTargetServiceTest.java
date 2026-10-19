package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carix.dashboard.dto.DashboardSummaryResponse.NegotiationTargets;
import com.carix.dashboard.dto.DashboardSummaryResponse.RecoveryTarget;
import com.carix.dashboard.dto.DashboardSummaryResponse.Target;
import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.WeightStatusEnum;
import com.carix.expense.service.ExpenseWeightResolver;
import com.carix.expense.service.MonthlyWeights;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.cpi.CategoryCpiRateService;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.WageBaseline;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.snapshot.SalaryGapBucket;
import com.carix.wagestat.WageStatSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 협상 목표 인상률 재료 조립 (기능 명세 US-D7·§4.8, 개발 계획 c8). */
@ExtendWith(OutputCaptureExtension.class)
class NegotiationTargetServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String BASE_YM = "202509";
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private CurrentSalaryProvider salaryProvider;
  private CareerProfileRepository profiles;
  private WageBaselineResolver baselines;
  private PeerSalaryDistributionResolver peers;
  private CategoryCpiRateService cpi;
  private ExpenseWeightResolver weights;
  private NegotiationTargetService service;

  @BeforeEach
  void setUp() {
    salaryProvider = mock(CurrentSalaryProvider.class);
    profiles = mock(CareerProfileRepository.class);
    baselines = mock(WageBaselineResolver.class);
    peers = mock(PeerSalaryDistributionResolver.class);
    cpi = mock(CategoryCpiRateService.class);
    weights = mock(ExpenseWeightResolver.class);
    service =
        new NegotiationTargetService(salaryProvider, profiles, baselines, peers, cpi, weights);

    givenSalary(50_000_000L, "2025-03-15");
    given(profiles.findByUserId(USER_ID))
        .willReturn(Optional.of(CareerProfile.builder().userId(USER_ID).build()));
    givenBaseline(60_000_000L);
    givenPeers(LongStream.rangeClosed(1, 10).map(i -> i * 10_000_000L).boxed().toList());
    givenCpi("202501", "202509", "0.01");
    givenUniformWeights();
  }

  private void givenSalary(long gross, String effectiveDate) {
    given(salaryProvider.currentGrossSalary(USER_ID, BASE_YM))
        .willReturn(Optional.of(new CurrentSalary(gross, LocalDate.parse(effectiveDate))));
  }

  private void givenBaseline(long median) {
    given(baselines.resolveBaseline(any(CareerProfile.class)))
        .willReturn(
            new WageBaseline(median, "ANNUAL_GROSS_MEDIAN", "202606", WageStatSource.KOSIS));
  }

  private void givenPeers(List<Long> disclosed) {
    given(peers.resolve(any(CareerProfile.class)))
        .willReturn(new PeerSalaryDistribution(disclosed, disclosed.size(), 0, "전국"));
  }

  /** {@code (first, latest]} 매달 모든 카테고리 같은 전월 대비 변화율. */
  private void givenCpi(String first, String latest, String monthlyRate) {
    Map<String, Map<ExpenseCategoryEnum, BigDecimal>> rates = new TreeMap<>();
    for (YearMonth m = YearMonth.parse(first, YM).plusMonths(1);
        !m.isAfter(YearMonth.parse(latest, YM));
        m = m.plusMonths(1)) {
      Map<ExpenseCategoryEnum, BigDecimal> r = new EnumMap<>(ExpenseCategoryEnum.class);
      for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
        r.put(c, new BigDecimal(monthlyRate));
      }
      rates.put(YM.format(m), r);
    }
    given(cpi.firstCpiMonth()).willReturn(Optional.of(first));
    given(cpi.latestCpiMonth(anyString())).willReturn(Optional.of(latest));
    given(cpi.monthOverMonth(anyString(), anyString())).willReturn(rates);
  }

  private void givenUniformWeights() {
    given(weights.resolveMonths(any(), anyList()))
        .willAnswer(
            inv -> {
              Map<String, MonthlyWeights> out = new LinkedHashMap<>();
              for (String ym : inv.<List<String>>getArgument(1)) {
                Map<ExpenseCategoryEnum, BigDecimal> w = new EnumMap<>(ExpenseCategoryEnum.class);
                Map<ExpenseCategoryEnum, WeightStatusEnum> s =
                    new EnumMap<>(ExpenseCategoryEnum.class);
                for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
                  w.put(c, new BigDecimal("0.1666666667"));
                  s.put(c, WeightStatusEnum.DEFAULT_CPI);
                }
                out.put(ym, new MonthlyWeights(w, s));
              }
              return out;
            });
  }

  private static String bucket(long median, long salary) {
    return SalaryGapBucket.of(SalaryGapBucket.ratio(median, salary)).name();
  }

  @Test
  @DisplayName("세 줄 — 임금통계 20%, p75 60%, 2025년 3월 이후 물가 6개월 1%씩 6.2%")
  void 세줄_정상() {
    NegotiationTargets targets =
        service.resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L));

    assertThat(targets.toWageStatMedian()).isEqualTo(new Target(20.0, "BELOW"));
    // p75 = get(7) = 8천만 → (80 − 50) / 50
    assertThat(targets.toPeerP75()).isEqualTo(new Target(60.0, "BELOW"));
    assertThat(targets.toPurchasingPowerRecovery())
        .isEqualTo(new RecoveryTarget(6.2, "BELOW", "202503"));
  }

  @Test
  @DisplayName("연봉이 없으면 세 줄 모두 null")
  void 연봉없음_세줄null() {
    given(salaryProvider.currentGrossSalary(USER_ID, BASE_YM)).willReturn(Optional.empty());

    NegotiationTargets targets = service.resolve(USER_ID, BASE_YM, "BELOW_MARKET_15_PLUS");

    assertThat(targets).isEqualTo(new NegotiationTargets(null, null, null));
  }

  @Test
  @DisplayName("프로필이 없으면 임금통계는 기본 프로필로 내고 p75 는 연봉 분포 화면처럼 내지 않는다")
  void 프로필없음_기본프로필_p75null() {
    given(profiles.findByUserId(USER_ID)).willReturn(Optional.empty());

    NegotiationTargets targets =
        service.resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L));

    assertThat(targets.toWageStatMedian()).isEqualTo(new Target(20.0, "BELOW"));
    assertThat(targets.toPeerP75()).isNull();
    verify(peers, never()).resolve(any());
  }

  @Test
  @DisplayName("복구선 — 이번 달에 연봉이 바뀌었으면 물가 상승분 없음")
  void recovery_이번달변경_NONE() {
    givenSalary(50_000_000L, "2025-09-01");

    assertThat(
            service
                .resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L))
                .toPurchasingPowerRecovery())
        .isEqualTo(new RecoveryTarget(null, "NONE", "202509"));
  }

  @Test
  @DisplayName("복구선 — CPI 가 2개월 넘게 늦으면 지우고 다른 줄은 그대로")
  void recovery_CPI지연_null() {
    givenCpi("202501", "202506", "0.01");

    NegotiationTargets targets =
        service.resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L));

    assertThat(targets.toPurchasingPowerRecovery()).isNull();
    assertThat(targets.toWageStatMedian()).isNotNull();
    assertThat(targets.toPeerP75()).isNotNull();
  }

  @Test
  @DisplayName("복구선 — CPI 가 2개월 늦은 것까지는 마지막 CPI 월까지 누적한다")
  void recovery_CPI허용시차_마지막월까지() {
    givenCpi("202501", "202507", "0.01");

    // 202504 ~ 202507 네 달
    assertThat(
            service
                .resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L))
                .toPurchasingPowerRecovery())
        .isEqualTo(new RecoveryTarget(4.1, "BELOW", "202503"));
  }

  @Test
  @DisplayName("복구선 — 연봉 변경이 CPI 수집 전이면 CPI 첫 달부터 세고 그 달을 밝힌다")
  void recovery_CPI시작전변경_since당김() {
    givenSalary(50_000_000L, "2019-05-01");

    assertThat(
            service
                .resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L))
                .toPurchasingPowerRecovery()
                .sinceYearMonth())
        .isEqualTo("202501");
  }

  @Test
  @DisplayName("복구선 — 구간 안 한 달이라도 카테고리 변화율이 빠지면 지운다")
  void recovery_카테고리결측_null() {
    Map<String, Map<ExpenseCategoryEnum, BigDecimal>> rates = new TreeMap<>();
    Map<ExpenseCategoryEnum, BigDecimal> partial = new EnumMap<>(ExpenseCategoryEnum.class);
    partial.put(ExpenseCategoryEnum.values()[0], new BigDecimal("0.01"));
    for (int m = 4; m <= 9; m++) {
      rates.put("20250" + m, partial);
    }
    given(cpi.monthOverMonth(anyString(), anyString())).willReturn(rates);

    assertThat(
            service
                .resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L))
                .toPurchasingPowerRecovery())
        .isNull();
  }

  @Test
  @DisplayName("동료집단 분포 조회가 실패해도 그 줄만 지우고 나머지는 낸다 — 선택 블록이 요약 전체를 깨지 않는다")
  void p75조회실패_그줄만null() {
    given(peers.resolve(any(CareerProfile.class))).willThrow(new IllegalStateException("db down"));

    NegotiationTargets targets =
        service.resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L));

    assertThat(targets.toPeerP75()).isNull();
    assertThat(targets.toWageStatMedian()).isEqualTo(new Target(20.0, "BELOW"));
    assertThat(targets.toPurchasingPowerRecovery()).isNotNull();
  }

  @Test
  @DisplayName("CPI 조회가 실패해도 복구선만 지운다")
  void CPI조회실패_복구선만null() {
    given(cpi.monthOverMonth(anyString(), anyString()))
        .willThrow(new IllegalStateException("db down"));

    NegotiationTargets targets =
        service.resolve(USER_ID, BASE_YM, bucket(60_000_000L, 50_000_000L));

    assertThat(targets.toPurchasingPowerRecovery()).isNull();
    assertThat(targets.toWageStatMedian()).isNotNull();
  }

  @Test
  @DisplayName("연봉 조회가 실패하면 세 줄 모두 null — 예외를 던지지 않는다")
  void 연봉조회실패_세줄null() {
    given(salaryProvider.currentGrossSalary(USER_ID, BASE_YM))
        .willThrow(new IllegalStateException("decrypt failed"));

    assertThat(service.resolve(USER_ID, BASE_YM, "BELOW_MARKET_15_PLUS"))
        .isEqualTo(new NegotiationTargets(null, null, null));
  }

  /** 회귀 가드 — 지금 이 경로에는 로그 문장이 없다. 나중에 로그·예외 메시지가 금액을 싣으면 잡는다 (완료 기준 10). */
  @Test
  @DisplayName("연봉·기준선·p75 금액이 로그에 남지 않는다")
  void 금액은_로그에_남지_않는다_회귀가드(CapturedOutput output) {
    givenSalary(54_321_000L, "2025-03-15");
    givenBaseline(61_234_000L);
    givenPeers(
        LongStream.rangeClosed(1, 10)
            .map(i -> i == 8 ? 77_777_000L : i * 7_000_000L)
            .sorted()
            .boxed()
            .toList());

    service.resolve(USER_ID, BASE_YM, bucket(61_234_000L, 54_321_000L));

    assertThat(output)
        .doesNotContain(
            "54321000", "54,321,000", "61234000", "61,234,000", "77777000", "77,777,000");
  }
}
