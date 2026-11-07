package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.dto.ExpenseItemInput;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.service.ExpenseService;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.service.IncomeService;
import com.carix.index.cpi.CategoryCpiRateService;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import com.carix.support.H2DataVersionIssuerConfiguration;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 과거 소비를 고치면 그 달을 상속하던 뒤 달들의 구매력까지 새 버전으로 다시 저장된다 (기능 명세 US-R1·§4.4, 도메인 정의서 §1.1).
 *
 * <p>재계산 배선({@code SnapshotTriggerWiringTest})과 전파 해석({@code ExpenseWeightResolverTest})은 따로 검증돼
 * 있다. 이 테스트는 둘을 실제로 엮는다 — 소비 저장 → 가중치 전파 → 구매력 엔진 → 스냅샷 저장. CPI 만 고정 변화율로 갈아끼운다(식품만 매달 오른다).
 */
@SpringBootTest
@Import(H2DataVersionIssuerConfiguration.class)
class ExpensePropagationBackfillTest {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final YearMonth NOW = YearMonth.parse(YearMonths.current(), YM);
  private static final YearMonth BASE = NOW.minusMonths(4);

  @Autowired private IncomeService incomeService;
  @Autowired private ExpenseService expenseService;
  @Autowired private IndexSnapshotRepository snapshotRepository;

  @MockitoBean private CategoryCpiRateService categoryCpiRateService;

  @Test
  @DisplayName("기준월 소비를 고치면 상속하던 뒤 달 구매력이 바뀌고 데이터 버전이 올라간다")
  void 과거_소비_수정이_뒤_달로_전파된다() {
    UUID userId = UUID.randomUUID();
    givenFoodInflation();
    incomeService.create(userId, new IncomeRequest(60_000_000L, 4_000_000L, BASE.atDay(1), null));

    // 기준월만 완전 입력 — 뒤 네 달은 기록이 없어 기준월을 상속한다(PROPAGATED). 식품 비중 10%
    expenseService.upsert(userId, YM.format(BASE), expenses(100_000L));
    Map<String, IndexSnapshot> before = latestByMonth(userId);

    // 식품 비중을 50% 로 고친다 — 식품만 오르므로 뒤 달 개인물가가 더 오르고 구매력이 더 내려가야 한다
    expenseService.upsert(userId, YM.format(BASE), expenses(500_000L));
    Map<String, IndexSnapshot> after = latestByMonth(userId);

    for (int i = 1; i <= 4; i++) {
      String month = YM.format(BASE.plusMonths(i));
      IndexSnapshot old = before.get(month);
      IndexSnapshot now = after.get(month);
      assertThat(old.getPurchasingPowerIndex()).as(month).isNotNull();
      assertThat(now.getPurchasingPowerIndex()).as(month).isLessThan(old.getPurchasingPowerIndex());
      assertThat(now.getDataVersion()).as(month).isNotEqualTo(old.getDataVersion());
    }
  }

  @Test
  @DisplayName("현재월에 처음 넣은 소비 구조가 과거 달 구매력에 반영된다")
  void 소비_구조가_다르면_구매력이_다르다() {
    // 회귀: 온보딩 소비는 현재월로 저장되는데 재계산이 그 달부터만 돌아, 앞선 달 스냅샷이 기본 가중치로 남았다 —
    // 소비 구조가 정반대인 두 사용자의 구매력이 같았다(운영 실측 93.2 동일).
    givenFoodInflation();
    UUID foodHeavy = UUID.randomUUID();
    UUID foodLight = UUID.randomUUID();
    for (UUID userId : List.of(foodHeavy, foodLight)) {
      incomeService.create(userId, new IncomeRequest(60_000_000L, 4_000_000L, BASE.atDay(1), null));
    }

    expenseService.upsert(foodHeavy, YM.format(NOW), expenses(2_000_000L));
    expenseService.upsert(foodLight, YM.format(NOW), expenses(10_000L));

    String past = YM.format(BASE.plusMonths(2));
    assertThat(latestByMonth(foodHeavy).get(past).getPurchasingPowerIndex())
        .isLessThan(latestByMonth(foodLight).get(past).getPurchasingPowerIndex());
  }

  /** 식품은 다른 다섯 카테고리 합계(500,000원)와 함께 6개 전부 입력한다 — 완전 입력이어야 뒤 달로 전파된다. */
  private ExpenseUpdateRequest expenses(long food) {
    List<ExpenseItemInput> items =
        Arrays.stream(ExpenseCategoryEnum.values())
            .map(
                c ->
                    new ExpenseItemInput(c.name(), c == ExpenseCategoryEnum.FOOD ? food : 100_000L))
            .toList();
    return new ExpenseUpdateRequest(items);
  }

  private void givenFoodInflation() {
    given(categoryCpiRateService.latestCpiMonth(anyString()))
        .willAnswer(call -> Optional.of(call.getArgument(0, String.class)));
    given(categoryCpiRateService.monthOverMonth(anyString(), anyString()))
        .willAnswer(
            call -> {
              YearMonth from = YearMonth.parse(call.getArgument(0, String.class), YM);
              YearMonth to = YearMonth.parse(call.getArgument(1, String.class), YM);
              Map<String, Map<ExpenseCategoryEnum, BigDecimal>> rates = new TreeMap<>();
              for (YearMonth m = from.plusMonths(1); !m.isAfter(to); m = m.plusMonths(1)) {
                Map<ExpenseCategoryEnum, BigDecimal> month =
                    new EnumMap<>(ExpenseCategoryEnum.class);
                for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
                  month.put(
                      c, c == ExpenseCategoryEnum.FOOD ? new BigDecimal("0.05") : BigDecimal.ZERO);
                }
                rates.put(YM.format(m), month);
              }
              return rates;
            });
  }

  private Map<String, IndexSnapshot> latestByMonth(UUID userId) {
    return snapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId).stream()
        .collect(
            Collectors.toMap(
                IndexSnapshot::getBaseYearMonth,
                Function.identity(),
                (a, b) -> a.getDataVersion().compareTo(b.getDataVersion()) >= 0 ? a : b));
  }
}
