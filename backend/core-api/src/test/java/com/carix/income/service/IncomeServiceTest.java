package com.carix.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.dto.IncomeResponse;
import com.carix.income.exception.DuplicateEffectiveDateException;
import com.carix.income.exception.IncomeNotFoundException;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.snapshot.SnapshotRecalculationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IncomeServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private IncomeHistoryRepository repository;
  private IncomeService service;

  @BeforeEach
  void setUp() {
    repository = mock(IncomeHistoryRepository.class);
    service = new IncomeService(repository, mock(SnapshotRecalculationService.class));
  }

  @Test
  @DisplayName("getHistory - 유저의 소득 목록 조회 및 변환")
  void getHistory() {
    IncomeHistory history =
        new IncomeHistory(USER_ID, 60000000L, 4500000L, LocalDate.of(2026, 1, 1));
    given(repository.findByUserIdOrderByEffectiveDateDesc(USER_ID)).willReturn(List.of(history));

    List<IncomeResponse> result = service.getHistory(USER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).grossSalary()).isEqualTo(60000000L);
    assertThat(result.get(0).netSalary()).isEqualTo(4500000L);
  }

  @Test
  @DisplayName("create - 성공적으로 저장")
  void create_success() {
    LocalDate date = LocalDate.of(2026, 7, 1);
    IncomeRequest req = new IncomeRequest(65000000L, 4800000L, date);

    given(repository.existsByUserIdAndEffectiveDate(USER_ID, date)).willReturn(false);
    given(repository.save(any(IncomeHistory.class))).willAnswer(i -> i.getArgument(0));

    IncomeResponse res = service.create(USER_ID, req);

    assertThat(res.grossSalary()).isEqualTo(65000000L);
    assertThat(res.netSalary()).isEqualTo(4800000L);
    assertThat(res.effectiveDate()).isEqualTo(date);
  }

  @Test
  @DisplayName("create - 동일 시작일 중복 시 DuplicateEffectiveDateException")
  void create_duplicate() {
    LocalDate date = LocalDate.of(2026, 7, 1);
    IncomeRequest req = new IncomeRequest(65000000L, 4800000L, date);

    given(repository.existsByUserIdAndEffectiveDate(USER_ID, date)).willReturn(true);

    assertThatThrownBy(() -> service.create(USER_ID, req))
        .isInstanceOf(DuplicateEffectiveDateException.class);
  }

  @Test
  @DisplayName("update - 성공적으로 수정")
  void update_success() {
    UUID id = UUID.randomUUID();
    LocalDate oldDate = LocalDate.of(2026, 1, 1);
    LocalDate newDate = LocalDate.of(2026, 2, 1);
    IncomeHistory history = new IncomeHistory(USER_ID, 60000000L, 4500000L, oldDate);

    given(repository.findById(id)).willReturn(Optional.of(history));
    given(repository.existsByUserIdAndEffectiveDateAndIdNot(USER_ID, newDate, id))
        .willReturn(false);

    IncomeRequest req = new IncomeRequest(70000000L, 5000000L, newDate);
    IncomeResponse res = service.update(USER_ID, id, req);

    assertThat(res.grossSalary()).isEqualTo(70000000L);
    assertThat(res.netSalary()).isEqualTo(5000000L);
    assertThat(res.effectiveDate()).isEqualTo(newDate);
  }

  @Test
  @DisplayName("update - 항목이 없거나 타인 항목이면 IncomeNotFoundException")
  void update_notFound() {
    UUID id = UUID.randomUUID();
    given(repository.findById(id)).willReturn(Optional.empty());

    IncomeRequest req = new IncomeRequest(70000000L, 5000000L, LocalDate.now());

    assertThatThrownBy(() -> service.update(USER_ID, id, req))
        .isInstanceOf(IncomeNotFoundException.class);
  }

  @Test
  @DisplayName("update - 수정하려는 시작일이 다른 항목과 중복되면 DuplicateEffectiveDateException")
  void update_duplicateDate() {
    UUID id = UUID.randomUUID();
    LocalDate newDate = LocalDate.of(2026, 2, 1);
    IncomeHistory history =
        new IncomeHistory(USER_ID, 60000000L, 4500000L, LocalDate.of(2026, 1, 1));

    given(repository.findById(id)).willReturn(Optional.of(history));
    given(repository.existsByUserIdAndEffectiveDateAndIdNot(USER_ID, newDate, id)).willReturn(true);

    IncomeRequest req = new IncomeRequest(70000000L, 5000000L, newDate);

    assertThatThrownBy(() -> service.update(USER_ID, id, req))
        .isInstanceOf(DuplicateEffectiveDateException.class);
  }

  @Test
  @DisplayName("delete - 성공적으로 삭제")
  void delete_success() {
    UUID id = UUID.randomUUID();
    IncomeHistory history =
        new IncomeHistory(USER_ID, 60000000L, 4500000L, LocalDate.of(2026, 1, 1));

    given(repository.findById(id)).willReturn(Optional.of(history));

    service.delete(USER_ID, id);

    verify(repository).delete(history);
  }

  @Test
  @DisplayName("delete - 존재하지 않는 경우 IncomeNotFoundException")
  void delete_notFound() {
    UUID id = UUID.randomUUID();
    given(repository.findById(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(USER_ID, id))
        .isInstanceOf(IncomeNotFoundException.class);
  }
}
