package com.carix.expense.service;

import static com.carix.expense.domain.ExpenseCategoryEnum.EDUCATION;
import static com.carix.expense.domain.ExpenseCategoryEnum.FOOD;
import static com.carix.expense.domain.ExpenseCategoryEnum.HOUSING;
import static com.carix.expense.domain.ExpenseCategoryEnum.LEISURE;
import static com.carix.expense.domain.ExpenseCategoryEnum.MEDICAL;
import static com.carix.expense.domain.ExpenseCategoryEnum.TRANSPORT;
import static com.carix.expense.domain.WeightStatusEnum.DEFAULT_CPI;
import static com.carix.expense.domain.WeightStatusEnum.USER_DEFINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.domain.ExpenseItemEntity;
import com.carix.expense.domain.WeightStatusEnum;
import com.carix.expense.dto.ExpenseHistoryResponse;
import com.carix.expense.dto.ExpenseItemInput;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.exception.InvalidExpenseCategoryException;
import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.expense.service.ExpenseWeightResolver.ResolvedCategory;
import com.carix.snapshot.SnapshotRecalculationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * 저장 경로(upsert)는 "사용자가 입력한 카테고리만 행으로 존재"하는 것을 검증한다. 6개 채우기·상태 도출은 ExpenseWeightResolver 몫이라 여기서는
 * mock 으로 대체하고, 그 결과가 응답 DTO 로 옮겨지는지만 본다. Step 1~4 로직 자체는 ExpenseWeightResolverTest 에서 검증한다.
 */
class ExpenseServiceTest {

  private ExpenseHistoryRepository repository;
  private SnapshotRecalculationService snapshotRecalculationService;
  private ExpenseWeightResolver weightResolver;
  private ExpenseService expenseService;

  @BeforeEach
  void setUp() {
    repository = mock(ExpenseHistoryRepository.class);
    snapshotRecalculationService = mock(SnapshotRecalculationService.class);
    weightResolver = mock(ExpenseWeightResolver.class);
    expenseService = new ExpenseService(repository, snapshotRecalculationService, weightResolver);
  }

  @Test
  @DisplayName("최초 소비 등록 시 입력한 카테고리만 저장하고, 저장 후 스냅샷 재계산을 트리거한다")
  void upsert_initialOnboarding_storesOnlyEnteredCategories() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202609";

    given(repository.findByUserIdAndBaseYearMonth(userId, baseYearMonth))
        .willReturn(Optional.empty());
    given(repository.save(any(ExpenseHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(weightResolver.resolve(any(ExpenseHistory.class)))
        .willReturn(
            List.of(
                resolved(HOUSING, 700000L, USER_DEFINED),
                resolved(FOOD, 500000L, USER_DEFINED),
                resolved(TRANSPORT, 150000L, DEFAULT_CPI),
                resolved(MEDICAL, 150000L, DEFAULT_CPI),
                resolved(EDUCATION, 150000L, DEFAULT_CPI),
                resolved(LEISURE, 150000L, DEFAULT_CPI)));

    ExpenseUpdateRequest request =
        new ExpenseUpdateRequest(
            List.of(
                new ExpenseItemInput("FOOD", 500000L), new ExpenseItemInput("HOUSING", 700000L)));

    ExpenseHistoryResponse response = expenseService.upsert(userId, baseYearMonth, request);

    // 저장은 입력한 2개만 — 6개 보완 행을 만들지 않는다
    ArgumentCaptor<ExpenseHistory> captor = ArgumentCaptor.forClass(ExpenseHistory.class);
    InOrder inOrder = inOrder(repository, snapshotRecalculationService);
    inOrder.verify(repository).save(captor.capture());
    inOrder.verify(snapshotRecalculationService).recalculateSnapshot(userId, baseYearMonth);

    assertThat(captor.getValue().getItems())
        .extracting(ExpenseItemEntity::getCategory, ExpenseItemEntity::getAmount)
        .containsExactlyInAnyOrder(tuple(FOOD, 500000L), tuple(HOUSING, 700000L));

    // 응답은 리졸버가 채운 6개 + 혼합 상태 라벨
    assertThat(response.baseYearMonth()).isEqualTo(baseYearMonth);
    assertThat(response.items()).hasSize(6);
    assertThat(response.weightStatus()).isEqualTo("PARTIALLY_ESTIMATED");
    var transport =
        response.items().stream()
            .filter(i -> "TRANSPORT".equals(i.category()))
            .findFirst()
            .orElseThrow();
    assertThat(transport.amount()).isEqualTo(150000L);
    assertThat(transport.status()).isEqualTo("DEFAULT_CPI");
  }

  @Test
  @DisplayName("잘못된 카테고리 이름 입력 시 InvalidExpenseCategoryException 발생, 저장·재계산 없음")
  void upsert_invalidCategory_throwsException() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202609";

    ExpenseUpdateRequest request =
        new ExpenseUpdateRequest(List.of(new ExpenseItemInput("INVALID_CAT", 500000L)));

    assertThatThrownBy(() -> expenseService.upsert(userId, baseYearMonth, request))
        .isInstanceOf(InvalidExpenseCategoryException.class);

    verify(repository, never()).save(any(ExpenseHistory.class));
    verifyNoInteractions(snapshotRecalculationService);
  }

  // ---------------------------------------------------------------------------

  private static ResolvedCategory resolved(
      ExpenseCategoryEnum category, long amount, WeightStatusEnum status) {
    return new ResolvedCategory(category, amount, status);
  }
}
