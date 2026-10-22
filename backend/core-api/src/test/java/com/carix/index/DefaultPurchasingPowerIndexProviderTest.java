package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.WeightStatusEnum;
import com.carix.expense.service.ExpenseWeightResolver;
import com.carix.expense.service.MonthlyWeights;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.cpi.CategoryCpiRateService;
import com.carix.index.dto.PpiResult;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultPurchasingPowerIndexProviderTest {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private final UUID userId = UUID.randomUUID();

  private IncomeHistoryRepository incomeRepository;
  private ExpenseWeightResolver weightResolver;
  private CategoryCpiRateService cpiRates;
  private DefaultPurchasingPowerIndexProvider provider;

  @BeforeEach
  void setUp() {
    incomeRepository = mock(IncomeHistoryRepository.class);
    weightResolver = mock(ExpenseWeightResolver.class);
    cpiRates = mock(CategoryCpiRateService.class);
    provider = new DefaultPurchasingPowerIndexProvider(incomeRepository, weightResolver, cpiRates);
    givenWeights(WeightStatusEnum.DEFAULT_CPI);
  }

  private void givenIncomes(IncomeHistory... incomes) {
    List<IncomeHistory> desc =
        java.util.Arrays.stream(incomes)
            .sorted((a, b) -> b.getEffectiveDate().compareTo(a.getEffectiveDate()))
            .toList();
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(userId)).willReturn(desc);
  }

  private IncomeHistory income(String date, long gross, Long net) {
    return new IncomeHistory(userId, gross, net, LocalDate.parse(date), null);
  }

  /** {@code (from, to]} 매달 모든 카테고리 같은 변화율, 첫 CPI 월은 {@code from}, 최신 CPI 월은 {@code to}. */
  private void givenCpi(String from, String to, String monthlyRate) {
    Map<String, Map<ExpenseCategoryEnum, BigDecimal>> rates = new TreeMap<>();
    for (YearMonth m = YearMonth.parse(from, YM).plusMonths(1);
        !m.isAfter(YearMonth.parse(to, YM));
        m = m.plusMonths(1)) {
      Map<ExpenseCategoryEnum, BigDecimal> r = new EnumMap<>(ExpenseCategoryEnum.class);
      for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
        r.put(c, new BigDecimal(monthlyRate));
      }
      rates.put(YM.format(m), r);
    }
    given(cpiRates.firstCpiMonth()).willReturn(Optional.of(from));
    given(cpiRates.latestCpiMonth(anyString())).willReturn(Optional.of(to));
    given(cpiRates.monthOverMonth(anyString(), anyString())).willReturn(rates);
  }

  /** 모든 달 같은 상태, 균등 가중치. */
  private void givenWeights(WeightStatusEnum status) {
    given(weightResolver.resolveMonths(any(), anyList()))
        .willAnswer(
            inv -> {
              Map<String, MonthlyWeights> out = new LinkedHashMap<>();
              for (String ym : inv.<List<String>>getArgument(1)) {
                out.put(ym, uniform(status));
              }
              return out;
            });
  }

  private static MonthlyWeights uniform(WeightStatusEnum status) {
    Map<ExpenseCategoryEnum, BigDecimal> w = new EnumMap<>(ExpenseCategoryEnum.class);
    Map<ExpenseCategoryEnum, WeightStatusEnum> s = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
      w.put(c, new BigDecimal("0.1666666667"));
      s.put(c, status);
    }
    return new MonthlyWeights(w, s);
  }

  @Test
  @DisplayName("입력이 유효하지 않거나 연봉 이력이 없으면 NOT_AVAILABLE")
  void notAvailable() {
    assertThat(provider.calculate(null, "202607").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(provider.calculate(userId, "2026-07").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    givenIncomes();
    assertThat(provider.calculate(userId, "202607").value()).isNull();
  }

  @Test
  @DisplayName("기준월은 100.0 · INSUFFICIENT_HISTORY 이고 기준월이 첫 연봉 달이다")
  void baseMonth() {
    givenIncomes(income("2026-02-01", 60_000_000L, null));
    givenCpi("202602", "202602", "0.01");

    PpiResult r = provider.calculate(userId, "202602");

    assertThat(r.value()).isEqualByComparingTo("100.0");
    assertThat(r.status()).isEqualTo(IndexStatus.INSUFFICIENT_HISTORY);
    assertThat(r.indexBaseYearMonth()).isEqualTo("202602");
  }

  @Test
  @DisplayName("연봉 1건이어도 물가가 오르면 100 아래다")
  void singleIncome_pricesRise_valueBelow100() {
    givenIncomes(income("2026-02-01", 60_000_000L, null));
    givenCpi("202602", "202604", "0.01");

    PpiResult r = provider.calculate(userId, "202604");

    // 100 ÷ 1.0201 = 98.03
    assertThat(r.value()).isEqualByComparingTo("98.0");
    assertThat(r.status()).isEqualTo(IndexStatus.INSUFFICIENT_HISTORY);
  }

  @Test
  @DisplayName("기준월부터 12개월이 지나면 OK")
  void twelveMonths_ok() {
    givenIncomes(income("2025-02-01", 60_000_000L, null));
    givenCpi("202502", "202602", "0");

    assertThat(provider.calculate(userId, "202601").status())
        .isEqualTo(IndexStatus.INSUFFICIENT_HISTORY);
    assertThat(provider.calculate(userId, "202602").status()).isEqualTo(IndexStatus.OK);
  }

  @Test
  @DisplayName("현재월 CPI 가 없으면 허용 시차 안의 마지막 CPI 월까지 누적하고 cpiAsOf 가 그 달 말일이다")
  void currentMonthWithoutCpi_usesLastCpiMonth() {
    givenIncomes(income("2026-02-01", 60_000_000L, null));
    givenCpi("202602", "202609", "0");

    PpiResult r = provider.calculate(userId, "202610");

    assertThat(r.value()).isEqualByComparingTo("100.0");
    assertThat(r.cpiAsOf()).isEqualTo(LocalDate.of(2026, 9, 30));
  }

  @Test
  @DisplayName("CPI 가 허용 시차보다 늦으면 UPDATE_DELAYED, 값 없음")
  void cpiTooOld_updateDelayed() {
    givenIncomes(income("2026-02-01", 60_000_000L, null));
    givenCpi("202602", "202607", "0");

    PpiResult r = provider.calculate(userId, "202610");

    assertThat(r.status()).isEqualTo(IndexStatus.UPDATE_DELAYED);
    assertThat(r.value()).isNull();
  }

  @Test
  @DisplayName("첫 연봉이 최신 CPI 월보다 뒤면 누적 구간이 비어 100 이다")
  void baseAfterLastCpi_returns100() {
    givenIncomes(income("2026-10-01", 60_000_000L, null));
    givenCpi("202609", "202609", "0.01");

    PpiResult r = provider.calculate(userId, "202610");

    assertThat(r.value()).isEqualByComparingTo("100.0");
    assertThat(r.indexBaseYearMonth()).isEqualTo("202610");
  }

  @Test
  @DisplayName("첫 연봉이 CPI 수집 이전이면 첫 CPI 월이 기준월이고 기준 소득은 그 달까지 유효한 마지막 연봉이다")
  void incomeBeforeCpiCoverage_baseIsFirstCpiMonth() {
    givenIncomes(
        income("2019-06-01", 50_000_000L, null),
        income("2019-11-01", 60_000_000L, null),
        income("2020-03-01", 63_000_000L, null));
    givenCpi("202001", "202003", "0.01");

    PpiResult r = provider.calculate(userId, "202003");

    // 기준 소득은 2019-11 의 6천만. 105 ÷ 1.0201 = 102.93
    assertThat(r.indexBaseYearMonth()).isEqualTo("202001");
    assertThat(r.value()).isEqualByComparingTo("102.9");
    assertThat(r.status()).isEqualTo(IndexStatus.INSUFFICIENT_HISTORY);
  }

  @Test
  @DisplayName("기준월 이전 달은 CPI 시차와 무관하게 NOT_AVAILABLE 이다")
  void targetBeforeBaseMonth_notAvailable() {
    givenIncomes(income("2019-06-01", 60_000_000L, null));
    givenCpi("202001", "202609", "0.01");

    PpiResult r = provider.calculate(userId, "201912");

    assertThat(r.status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(r.value()).isNull();
  }

  @Test
  @DisplayName("구간 안 CPI 카테고리가 빠지면 NOT_AVAILABLE")
  void missingCpiCategory_notAvailable() {
    givenIncomes(income("2026-02-01", 60_000_000L, null));
    given(cpiRates.latestCpiMonth(anyString())).willReturn(Optional.of("202604"));
    given(cpiRates.monthOverMonth(anyString(), anyString())).willReturn(Map.of());

    assertThat(provider.calculate(userId, "202604").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
  }

  @Test
  @DisplayName("실수령액이 양쪽 다 있으면 실수령액으로 비교한다")
  void netOnBothSides_usesNet() {
    givenIncomes(
        income("2026-02-01", 60_000_000L, 48_000_000L),
        income("2026-03-01", 60_000_000L, 50_400_000L));
    givenCpi("202602", "202603", "0");

    assertThat(provider.calculate(userId, "202603").value()).isEqualByComparingTo("105.0");
  }

  @Test
  @DisplayName("실수령액이 한쪽에만 있으면 둘 다 세전으로 비교한다")
  void netOnlyOnOneSide_fallsBackToGross() {
    givenIncomes(
        income("2026-02-01", 60_000_000L, null), income("2026-03-01", 60_000_000L, 40_000_000L));
    givenCpi("202602", "202603", "0");

    assertThat(provider.calculate(userId, "202603").value()).isEqualByComparingTo("100.0");
  }

  @Test
  @DisplayName("가중치 출처는 대상월 카테고리 상태의 롤업이다")
  void weightStatusRollup() {
    givenIncomes(income("2026-02-01", 60_000_000L, null));
    givenCpi("202602", "202603", "0");

    givenWeights(WeightStatusEnum.USER_DEFINED);
    assertThat(provider.calculate(userId, "202603").weightStatus())
        .isEqualTo(ExpenseWeightStatus.USER_DEFINED);
    givenWeights(WeightStatusEnum.PROPAGATED);
    assertThat(provider.calculate(userId, "202603").weightStatus())
        .isEqualTo(ExpenseWeightStatus.PROPAGATED);

    given(weightResolver.resolveMonths(any(), anyList()))
        .willAnswer(
            inv -> {
              Map<String, MonthlyWeights> out = new LinkedHashMap<>();
              for (String ym : inv.<List<String>>getArgument(1)) {
                MonthlyWeights u = uniform(WeightStatusEnum.USER_DEFINED);
                Map<ExpenseCategoryEnum, WeightStatusEnum> s = new EnumMap<>(u.statuses());
                s.put(ExpenseCategoryEnum.LEISURE, WeightStatusEnum.DEFAULT_CPI);
                out.put(ym, new MonthlyWeights(u.weights(), s));
              }
              return out;
            });
    assertThat(provider.calculate(userId, "202603").weightStatus())
        .isEqualTo(ExpenseWeightStatus.PARTIALLY_ESTIMATED);
  }

  private IncomeHistory incomeWithBonus(String date, long gross, Long net, Long bonusAnnual) {
    IncomeHistory h = income(date, gross, net);
    h.applyBonusTerms(bonusAnnual == null, bonusAnnual);
    return h;
  }

  @Test
  @DisplayName("기준과 현재가 같은 상여 조건이면 상여 전과 같다")
  void 기준과_현재가_같은_상여조건이면_상여_전과_같다() {
    givenCpi("202602", "202604", "0.01");
    givenIncomes(income("2026-02-01", 60_000_000L, null), income("2026-03-01", 66_000_000L, null));
    BigDecimal before = provider.calculate(userId, "202604").value();

    givenIncomes(
        incomeWithBonus("2026-02-01", 60_000_000L, null, 6_000_000L),
        incomeWithBonus("2026-03-01", 66_000_000L, null, 6_600_000L));
    PpiResult after = provider.calculate(userId, "202604");

    // 비율이 66/60 에서 (66+6.6)/(60+6) 으로 같다 (1.1)
    assertThat(after.value()).isEqualByComparingTo(before);
  }

  @Test
  @DisplayName("현재 행만 따로 받음이면 소득 비율이 오른다")
  void 현재_행만_따로받음이면_소득비율이_오른다() {
    givenCpi("202602", "202604", "0");
    givenIncomes(
        income("2026-02-01", 60_000_000L, null),
        incomeWithBonus("2026-03-01", 60_000_000L, null, 6_000_000L));

    PpiResult r = provider.calculate(userId, "202604");

    assertThat(r.value()).isEqualByComparingTo("110.0");
  }

  @Test
  @DisplayName("현재 행이 연봉 0원이면 따로 받는 상여가 있어도 소득 비율을 내지 않는다")
  void 현재_행이_연봉_0원이면_상여를_소득으로_세지_않는다() {
    givenCpi("202602", "202604", "0");
    givenIncomes(
        income("2026-02-01", 60_000_000L, null),
        incomeWithBonus("2026-03-01", 0L, null, 6_000_000L));

    PpiResult r = provider.calculate(userId, "202604");

    assertThat(r.status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
  }

  @Test
  @DisplayName("실수령이 양쪽에 있으면 따로 받는 상여를 실효 수령률로 환산해 비교한다")
  void 실수령_양쪽이면_따로받음_상여를_수령률로_환산한다() {
    givenCpi("202602", "202604", "0");
    givenIncomes(
        income("2026-02-01", 60_000_000L, 4_000_000L),
        incomeWithBonus("2026-03-01", 60_000_000L, 4_000_000L, 6_000_000L));

    PpiResult r = provider.calculate(userId, "202604");

    // 월 실수령 4_000_000 -> 4_000_000 + 500_000 × 0.8 = 4_400_000
    assertThat(r.value()).isEqualByComparingTo("110.0");
  }
}
