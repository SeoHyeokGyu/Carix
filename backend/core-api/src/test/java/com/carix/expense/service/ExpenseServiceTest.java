package com.carix.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.domain.ExpenseItemEntity;
import com.carix.expense.domain.WeightStatusEnum;
import com.carix.expense.dto.ExpenseHistoryResponse;
import com.carix.expense.dto.ExpenseItemInput;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.exception.InvalidExpenseCategoryException;
import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.snapshot.SnapshotRecalculationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpenseServiceTest {

  private ExpenseHistoryRepository repository;
  private SnapshotRecalculationService snapshotRecalculationService;
  private ExpenseService expenseService;

  @BeforeEach
  void setUp() {
    repository = mock(ExpenseHistoryRepository.class);
    snapshotRecalculationService = mock(SnapshotRecalculationService.class);
    expenseService = new ExpenseService(repository, snapshotRecalculationService);
  }

  @Test
  @DisplayName("최초 온보딩 지출 등록 시 신규 ExpenseHistory 생성 후 저장 및 스냅샷 재계산 트리거")
  void upsert_initialOnboarding_success() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202609";

    given(repository.findByUserIdAndBaseYearMonth(userId, baseYearMonth))
        .willReturn(Optional.empty());
    given(repository.save(any(ExpenseHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    ExpenseUpdateRequest request =
        new ExpenseUpdateRequest(
            List.of(
                new ExpenseItemInput("FOOD", 500000L), new ExpenseItemInput("HOUSING", 700000L)));

    ExpenseHistoryResponse response = expenseService.upsert(userId, baseYearMonth, request);

    assertThat(response.baseYearMonth()).isEqualTo(baseYearMonth);
    assertThat(response.items()).hasSize(6); // 6대 카테고리 전체 보완
    assertThat(response.weightStatus()).isEqualTo("PARTIALLY_ESTIMATED");

    verify(repository).save(any(ExpenseHistory.class));
    verify(snapshotRecalculationService).recalculateSnapshot(userId, baseYearMonth);
  }

  @Test
  @DisplayName("다시 온보딩하기 재저장 시 기존 ExpenseHistory의 항목들을 in-place로 안전하게 갱신한다")
  void upsert_reOnboarding_updatesExistingItemsInPlace() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202609";

    ExpenseHistory existingHistory = new ExpenseHistory(userId, baseYearMonth);
    ExpenseItemEntity oldFood =
        new ExpenseItemEntity(ExpenseCategoryEnum.FOOD, 300000L, WeightStatusEnum.USER_DEFINED);
    existingHistory.replaceItems(List.of(oldFood));

    given(repository.findByUserIdAndBaseYearMonth(userId, baseYearMonth))
        .willReturn(Optional.of(existingHistory));
    given(repository.save(any(ExpenseHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // 재저장 요청: FOOD 금액 변경 및 TRANSPORT 추가
    ExpenseUpdateRequest request =
        new ExpenseUpdateRequest(
            List.of(
                new ExpenseItemInput("FOOD", 600000L), new ExpenseItemInput("TRANSPORT", 200000L)));

    ExpenseHistoryResponse response = expenseService.upsert(userId, baseYearMonth, request);

    assertThat(response.baseYearMonth()).isEqualTo(baseYearMonth);
    assertThat(response.items()).hasSize(6);

    // Food 항목이 600,000원으로 갱신되었는지 확인
    var foodItem =
        response.items().stream()
            .filter(i -> "FOOD".equals(i.category()))
            .findFirst()
            .orElseThrow();
    assertThat(foodItem.amount()).isEqualTo(600000L);
    assertThat(foodItem.status()).isEqualTo("USER_DEFINED");

    verify(repository).save(existingHistory);
    verify(snapshotRecalculationService).recalculateSnapshot(userId, baseYearMonth);
  }

  @Test
  @DisplayName("잘못된 카테고리 이름 입력 시 InvalidExpenseCategoryException 발생")
  void upsert_invalidCategory_throwsException() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202609";

    ExpenseUpdateRequest request =
        new ExpenseUpdateRequest(List.of(new ExpenseItemInput("INVALID_CAT", 500000L)));

    assertThatThrownBy(() -> expenseService.upsert(userId, baseYearMonth, request))
        .isInstanceOf(InvalidExpenseCategoryException.class);
  }
}
