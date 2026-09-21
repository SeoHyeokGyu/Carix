package com.carix.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.income.domain.IncomeChangeReasonEnum;
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
        new IncomeHistory(USER_ID, 60000000L, 4500000L, LocalDate.of(2026, 1, 1), null);
    given(repository.findByUserIdOrderByEffectiveDateDesc(USER_ID)).willReturn(List.of(history));

    List<IncomeResponse> result = service.getHistory(USER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).grossSalary()).isEqualTo(60000000L);
    assertThat(result.get(0).netSalary()).isEqualTo(4500000L);
  }

  @Test
  @DisplayName("getHistory - 가장 이른 유효일 행의 changeReason 이 INITIAL 로 파생되고 원값은 남는다")
  void getHistory_derivesInitial() {
    IncomeHistory newer =
        new IncomeHistory(
            USER_ID, 65000000L, 4500000L, LocalDate.of(2025, 1, 1), IncomeChangeReasonEnum.RAISE);
    IncomeHistory oldest =
        new IncomeHistory(
            USER_ID,
            58000000L,
            3800000L,
            LocalDate.of(2024, 1, 1),
            IncomeChangeReasonEnum.JOB_CHANGE);
    // 저장소는 effective_date DESC 로 돌려준다 — 마지막 원소가 최소 유효일이다.
    given(repository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of(newer, oldest));

    List<IncomeResponse> result = service.getHistory(USER_ID);

    assertThat(result.get(0).changeReason()).isEqualTo(IncomeChangeReasonEnum.RAISE);
    assertThat(result.get(1).changeReason()).isEqualTo(IncomeChangeReasonEnum.INITIAL);
    // D7 — 파생이 원값을 가리면 편집 폼이 그것을 지운다.
    assertThat(result.get(1).selectedChangeReason()).isEqualTo(IncomeChangeReasonEnum.JOB_CHANGE);
  }

  @Test
  @DisplayName("getHistory - 이력이 없으면 빈 목록이다")
  void getHistory_empty() {
    given(repository.findByUserIdOrderByEffectiveDateDesc(USER_ID)).willReturn(List.of());

    assertThat(service.getHistory(USER_ID)).isEmpty();
  }

  @Test
  @DisplayName("create - 더 이른 행이 없으면 단건 응답이 INITIAL 이다")
  void create_derivesInitialWhenEarliest() {
    LocalDate date = LocalDate.of(2024, 1, 1);
    IncomeRequest req = new IncomeRequest(58000000L, 3800000L, date, null);

    given(repository.existsByUserIdAndEffectiveDate(USER_ID, date)).willReturn(false);
    given(repository.existsByUserIdAndEffectiveDateBefore(USER_ID, date)).willReturn(false);
    given(repository.save(any(IncomeHistory.class))).willAnswer(i -> i.getArgument(0));

    IncomeResponse res = service.create(USER_ID, req);

    assertThat(res.changeReason()).isEqualTo(IncomeChangeReasonEnum.INITIAL);
    assertThat(res.selectedChangeReason()).isNull();
  }

  @Test
  @DisplayName("create - 더 이른 행이 있으면 단건 응답이 고른 값 그대로다")
  void create_keepsReasonWhenNotEarliest() {
    LocalDate date = LocalDate.of(2026, 7, 1);
    IncomeRequest req = new IncomeRequest(65000000L, 4800000L, date, IncomeChangeReasonEnum.RAISE);

    given(repository.existsByUserIdAndEffectiveDate(USER_ID, date)).willReturn(false);
    given(repository.existsByUserIdAndEffectiveDateBefore(USER_ID, date)).willReturn(true);
    given(repository.save(any(IncomeHistory.class))).willAnswer(i -> i.getArgument(0));

    IncomeResponse res = service.create(USER_ID, req);

    assertThat(res.changeReason()).isEqualTo(IncomeChangeReasonEnum.RAISE);
  }

  @Test
  @DisplayName("create - 성공적으로 저장")
  void create_success() {
    LocalDate date = LocalDate.of(2026, 7, 1);
    IncomeRequest req = new IncomeRequest(65000000L, 4800000L, date, null);

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
    IncomeRequest req = new IncomeRequest(65000000L, 4800000L, date, null);

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
    IncomeHistory history = new IncomeHistory(USER_ID, 60000000L, 4500000L, oldDate, null);

    given(repository.findById(id)).willReturn(Optional.of(history));
    given(repository.existsByUserIdAndEffectiveDateAndIdNot(USER_ID, newDate, id))
        .willReturn(false);

    IncomeRequest req = new IncomeRequest(70000000L, 5000000L, newDate, null);
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

    IncomeRequest req = new IncomeRequest(70000000L, 5000000L, LocalDate.now(), null);

    assertThatThrownBy(() -> service.update(USER_ID, id, req))
        .isInstanceOf(IncomeNotFoundException.class);
  }

  @Test
  @DisplayName("update - 수정하려는 시작일이 다른 항목과 중복되면 DuplicateEffectiveDateException")
  void update_duplicateDate() {
    UUID id = UUID.randomUUID();
    LocalDate newDate = LocalDate.of(2026, 2, 1);
    IncomeHistory history =
        new IncomeHistory(USER_ID, 60000000L, 4500000L, LocalDate.of(2026, 1, 1), null);

    given(repository.findById(id)).willReturn(Optional.of(history));
    given(repository.existsByUserIdAndEffectiveDateAndIdNot(USER_ID, newDate, id)).willReturn(true);

    IncomeRequest req = new IncomeRequest(70000000L, 5000000L, newDate, null);

    assertThatThrownBy(() -> service.update(USER_ID, id, req))
        .isInstanceOf(DuplicateEffectiveDateException.class);
  }

  @Test
  @DisplayName("update - 자기 행을 뺀 판정이라 유효일을 미뤄도 파생이 뒤집히지 않는다")
  void update_derivesExcludingSelf() {
    UUID id = UUID.randomUUID();
    LocalDate oldDate = LocalDate.of(2024, 1, 1);
    LocalDate newDate = LocalDate.of(2024, 6, 1);
    IncomeHistory history = new IncomeHistory(USER_ID, 58000000L, 3800000L, oldDate, null);

    given(repository.findById(id)).willReturn(Optional.of(history));
    given(repository.existsByUserIdAndEffectiveDateAndIdNot(USER_ID, newDate, id))
        .willReturn(false);
    given(repository.existsByUserIdAndEffectiveDateBeforeAndIdNot(USER_ID, newDate, id))
        .willReturn(false);

    IncomeResponse res =
        service.update(USER_ID, id, new IncomeRequest(60000000L, 4000000L, newDate, null));

    assertThat(res.changeReason()).isEqualTo(IncomeChangeReasonEnum.INITIAL);
  }

  @Test
  @DisplayName("delete - 성공적으로 삭제")
  void delete_success() {
    UUID id = UUID.randomUUID();
    IncomeHistory history =
        new IncomeHistory(USER_ID, 60000000L, 4500000L, LocalDate.of(2026, 1, 1), null);

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

  @Test
  @DisplayName("create - changeReason 이 INITIAL 이면 IllegalArgumentException")
  void create_rejectsInitial() {
    IncomeRequest req =
        new IncomeRequest(
            65000000L, 4800000L, LocalDate.of(2026, 7, 1), IncomeChangeReasonEnum.INITIAL);

    assertThatThrownBy(() -> service.create(USER_ID, req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INITIAL");
  }

  @Test
  @DisplayName("create - 고른 changeReason 을 그대로 저장한다")
  void create_storesChangeReason() {
    LocalDate date = LocalDate.of(2026, 7, 1);
    IncomeRequest req =
        new IncomeRequest(65000000L, 4800000L, date, IncomeChangeReasonEnum.JOB_CHANGE);

    given(repository.existsByUserIdAndEffectiveDate(USER_ID, date)).willReturn(false);
    given(repository.existsByUserIdAndEffectiveDateBefore(USER_ID, date)).willReturn(true);
    given(repository.save(any(IncomeHistory.class))).willAnswer(i -> i.getArgument(0));

    IncomeResponse res = service.create(USER_ID, req);

    assertThat(res.selectedChangeReason()).isEqualTo(IncomeChangeReasonEnum.JOB_CHANGE);
  }

  @Test
  @DisplayName("update - changeReason 미지정이면 기존 값을 지운다 (PUT 은 전체 교체다)")
  void update_clearsChangeReason() {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 1, 1);
    IncomeHistory history =
        new IncomeHistory(USER_ID, 60000000L, 4500000L, date, IncomeChangeReasonEnum.JOB_CHANGE);

    given(repository.findById(id)).willReturn(Optional.of(history));
    given(repository.existsByUserIdAndEffectiveDateAndIdNot(USER_ID, date, id)).willReturn(false);
    // 더 이른 행이 있으므로 이 행은 최소 유효일 행이 아니다 — changeReason() 이 INITIAL 로 파생되지 않는다.
    given(repository.existsByUserIdAndEffectiveDateBeforeAndIdNot(USER_ID, date, id))
        .willReturn(true);

    IncomeResponse res =
        service.update(USER_ID, id, new IncomeRequest(70000000L, 5000000L, date, null));

    assertThat(res.selectedChangeReason()).isNull();
    assertThat(history.getChangeReason()).isNull();
  }
}
