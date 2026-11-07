package com.carix.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.expense.config.DefaultExpenseWeightProvider;
import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.domain.ExpenseItemEntity;
import com.carix.expense.dto.AppliedExpenseResponse;
import com.carix.expense.repository.ExpenseHistoryRepository;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `US-O6` 검토 화면이 묻는 것은 "무엇이 실제로 적용됐나" 다. 통계청 평균을 그대로 보여주는 응답이 아니라, 리졸버가 입력·전파·보간 중 무엇을 썼는지에 따라 답이
 * 달라진다.
 *
 * <p>기본 가중치는 검산이 눈으로 되도록 HOUSING 0.30 / FOOD 0.30 / 나머지 넷 0.10 (합 1.0) 으로 고정한다 — {@code
 * ExpenseWeightResolverTest} 와 같은 방식이다.
 */
class AppliedExpenseServiceTest {

  private static final String YM = "202609";

  private ExpenseHistoryRepository repository;
  private DefaultExpenseWeightProvider weightProvider;
  private AppliedExpenseService service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    repository = mock(ExpenseHistoryRepository.class);
    weightProvider = mock(DefaultExpenseWeightProvider.class);

    given(weightProvider.getDefaultWeights()).willReturn(fixedWeights());
    given(weightProvider.getVersion()).willReturn(7);
    given(weightProvider.getBasePeriod()).willReturn("2025");
    given(repository.findByUserIdOrderByBaseYearMonthDesc(any())).willReturn(List.of());

    service =
        new AppliedExpenseService(
            repository, new ExpenseWeightResolver(repository, weightProvider), weightProvider);
  }

  @Test
  @DisplayName("과거 완전 입력 월이 있으면 그 금액이 전파된 것으로 적용된다")
  void 전파() {
    ExpenseHistory past = new ExpenseHistory(userId, "202608");
    past.replaceItems(fullMonth(400_000L));
    given(repository.findByUserIdOrderByBaseYearMonthDesc(userId)).willReturn(List.of(past));
    givenMonth(YM, List.of());

    AppliedExpenseResponse response = service.resolve(userId, YM);

    assertThat(response.amountBasis()).isEqualTo("PROPAGATED");
    assertThat(response.categories())
        .allSatisfy(
            category -> {
              assertThat(category.amount()).isEqualTo(400_000L);
              assertThat(category.weightStatus()).isEqualTo("PROPAGATED");
            });
  }

  /** 이 케이스가 이 API 의 존재 이유다 — 0 을 내보내면 "한 달 주거비 0원" 이라는 거짓이 된다. */
  @Test
  @DisplayName("절대 금액 기준이 없으면 금액은 null 이고 가중치만 적용된 것이다")
  void 금액_기준_없음() {
    givenMonth(YM, List.of());

    AppliedExpenseResponse response = service.resolve(userId, YM);

    assertThat(response.amountBasis()).isEqualTo("NONE");
    assertThat(response.categories())
        .hasSize(6)
        .allSatisfy(
            category -> {
              assertThat(category.amount()).isNull();
              assertThat(category.weight()).isNotNull();
              assertThat(category.weight()).isGreaterThan(BigDecimal.ZERO);
              assertThat(category.weightStatus()).isEqualTo("DEFAULT_CPI");
            });
  }

  @Test
  @DisplayName("그 달 이력 행이 아예 없어도 같은 모양으로 답한다")
  void 이력_없음() {
    given(repository.findByUserIdAndBaseYearMonth(userId, YM)).willReturn(Optional.empty());

    AppliedExpenseResponse response = service.resolve(userId, YM);

    assertThat(response.baseYearMonth()).isEqualTo(YM);
    assertThat(response.amountBasis()).isEqualTo("NONE");
    assertThat(response.categories()).hasSize(6);
  }

  @Test
  @DisplayName("기준월을 주지 않으면 최신 소비 이력 월로 답한다")
  void 기준월_생략() {
    ExpenseHistory latest = new ExpenseHistory(userId, "202608");
    latest.replaceItems(fullMonth(400_000L));
    ExpenseHistory older = new ExpenseHistory(userId, "202607");
    older.replaceItems(fullMonth(300_000L));
    given(repository.findByUserIdOrderByBaseYearMonthDesc(userId))
        .willReturn(List.of(latest, older));
    given(repository.findByUserIdAndBaseYearMonth(userId, "202608"))
        .willReturn(Optional.of(latest));

    AppliedExpenseResponse response = service.resolve(userId, null);

    assertThat(response.baseYearMonth()).isEqualTo("202608");
    assertThat(response.amountBasis()).isEqualTo("USER_INPUT");
  }

  @Test
  @DisplayName("사용자 입력 달은 입력 금액 비율을 가중치로 싣는다")
  void 사용자_입력_달은_입력_비율을_가중치로_싣는다() {
    // 회귀: 금액은 사용자 입력인데 가중치는 늘 통계청 기본값을 실어, 둘이 서로 다른 소비 구조를 말했다.
    givenMonth(
        YM,
        java.util.Arrays.stream(ExpenseCategoryEnum.values())
            .map(
                c ->
                    new ExpenseItemEntity(
                        c, c == ExpenseCategoryEnum.HOUSING ? 1_000_000L : 200_000L))
            .toList());

    AppliedExpenseResponse response = service.resolve(userId, YM);

    assertThat(response.categories())
        .filteredOn(c -> c.category().equals("HOUSING"))
        .singleElement()
        .satisfies(c -> assertThat(c.weight()).isEqualByComparingTo("0.5"));
    assertThat(response.categories())
        .filteredOn(c -> c.category().equals("FOOD"))
        .singleElement()
        .satisfies(c -> assertThat(c.weight()).isEqualByComparingTo("0.1"));
  }

  // --- 픽스처 ---

  private void givenMonth(String yearMonth, List<ExpenseItemEntity> items) {
    ExpenseHistory history = new ExpenseHistory(userId, yearMonth);
    history.replaceItems(items);
    given(repository.findByUserIdAndBaseYearMonth(userId, yearMonth))
        .willReturn(Optional.of(history));
  }

  private static List<ExpenseItemEntity> fullMonth(long amount) {
    return java.util.Arrays.stream(ExpenseCategoryEnum.values())
        .map(category -> new ExpenseItemEntity(category, amount))
        .toList();
  }

  private static Map<ExpenseCategoryEnum, BigDecimal> fixedWeights() {
    Map<ExpenseCategoryEnum, BigDecimal> weights = new EnumMap<>(ExpenseCategoryEnum.class);
    weights.put(ExpenseCategoryEnum.HOUSING, new BigDecimal("0.30"));
    weights.put(ExpenseCategoryEnum.FOOD, new BigDecimal("0.30"));
    weights.put(ExpenseCategoryEnum.TRANSPORT, new BigDecimal("0.10"));
    weights.put(ExpenseCategoryEnum.MEDICAL, new BigDecimal("0.10"));
    weights.put(ExpenseCategoryEnum.EDUCATION, new BigDecimal("0.10"));
    weights.put(ExpenseCategoryEnum.LEISURE, new BigDecimal("0.10"));
    return weights;
  }
}
