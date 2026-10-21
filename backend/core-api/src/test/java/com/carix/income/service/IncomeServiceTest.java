package com.carix.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.BonusTermsRequest;
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
  private SnapshotRecalculationService snapshotRecalculationService;
  private IncomeService service;

  @BeforeEach
  void setUp() {
    repository = mock(IncomeHistoryRepository.class);
    snapshotRecalculationService = mock(SnapshotRecalculationService.class);
    service = new IncomeService(repository, snapshotRecalculationService);
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
    // 행이 하나뿐이면 그 행이 곧 최소 유효일 행이다 — 가장 흔한 실사용 상태인데도
    // 경계(size == 1)에서 파생 분기가 실제로 도는지 여태 아무 테스트도 안 봤다.
    assertThat(result.get(0).changeReason()).isEqualTo(IncomeChangeReasonEnum.INITIAL);
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
    // 비제외 메서드를 반대값으로 스텁한다. 이게 없으면 Mockito 기본값 false 때문에 서비스가
    // 실수로 비제외 메서드를 불러도 결과가 같아, 이 테스트가 막으려는 회귀를 못 잡는다.
    given(repository.existsByUserIdAndEffectiveDateBefore(USER_ID, newDate)).willReturn(true);

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

    assertThat(res.changeReason()).isNull();
    assertThat(res.selectedChangeReason()).isNull();
    assertThat(history.getChangeReason()).isNull();
  }

  private static IncomeHistory row(LocalDate date) {
    return new IncomeHistory(USER_ID, 60000000L, 4500000L, date, null);
  }

  private static IncomeHistory rowWithId(UUID id, LocalDate date) {
    return IncomeHistory.builder()
        .id(id)
        .userId(USER_ID)
        .grossSalary(60000000L)
        .netSalary(4500000L)
        .effectiveDate(date)
        .build();
  }

  @Test
  @DisplayName("updateBonusTerms - applyFrom_이후_행에만_적용된다")
  void applyFrom_이후_행에만_적용된다() {
    UUID id2024 = UUID.randomUUID();
    UUID id2025 = UUID.randomUUID();
    UUID id2026 = UUID.randomUUID();
    IncomeHistory r2024 = rowWithId(id2024, LocalDate.of(2024, 3, 1));
    IncomeHistory r2025 = rowWithId(id2025, LocalDate.of(2025, 3, 1));
    IncomeHistory r2026 = rowWithId(id2026, LocalDate.of(2026, 3, 1));
    given(repository.findById(id2025)).willReturn(Optional.of(r2025));
    given(repository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of(r2026, r2025, r2024));

    List<IncomeResponse> res =
        service.updateBonusTerms(USER_ID, new BonusTermsRequest(id2025, false, 6_000_000L));

    assertThat(r2024.isBonusIncluded()).isTrue();
    assertThat(r2024.getFixedBonusAnnual()).isNull();
    assertThat(r2025.isBonusIncluded()).isFalse();
    assertThat(r2025.getFixedBonusAnnual()).isEqualTo(6_000_000L);
    assertThat(r2026.isBonusIncluded()).isFalse();
    assertThat(r2026.getFixedBonusAnnual()).isEqualTo(6_000_000L);
    assertThat(res).hasSize(3);
    assertThat(res.get(0).bonusIncluded()).isFalse();
    assertThat(res.get(0).fixedBonusAnnual()).isEqualTo(6_000_000L);
    assertThat(res.get(2).bonusIncluded()).isTrue();
    assertThat(res.get(2).fixedBonusAnnual()).isNull();
  }

  @Test
  @DisplayName("updateBonusTerms - 들어있음이면 고정상여는 null 로 저장한다")
  void 들어있음이면_고정상여는_서비스에서도_null로_저장한다() {
    UUID id = UUID.randomUUID();
    IncomeHistory r = rowWithId(id, LocalDate.of(2025, 3, 1));
    r.applyBonusTerms(false, 6_000_000L);
    given(repository.findById(id)).willReturn(Optional.of(r));
    given(repository.findByUserIdOrderByEffectiveDateDesc(USER_ID)).willReturn(List.of(r));

    List<IncomeResponse> res =
        service.updateBonusTerms(USER_ID, new BonusTermsRequest(id, true, 3_000_000L));

    assertThat(r.isBonusIncluded()).isTrue();
    assertThat(r.getFixedBonusAnnual()).isNull();
    assertThat(res.get(0).fixedBonusAnnual()).isNull();
  }

  @Test
  @DisplayName("updateBonusTerms - 상여_조건_변경은_스냅샷_재계산을_부른다")
  void 상여_조건_변경은_스냅샷_재계산을_부른다() {
    UUID id = UUID.randomUUID();
    IncomeHistory r = rowWithId(id, LocalDate.of(2025, 3, 1));
    given(repository.findById(id)).willReturn(Optional.of(r));
    given(repository.findByUserIdOrderByEffectiveDateDesc(USER_ID)).willReturn(List.of(r));

    service.updateBonusTerms(USER_ID, new BonusTermsRequest(id, true, null));

    verify(snapshotRecalculationService).recalculateSnapshot(USER_ID, "202503");
  }

  @Test
  @DisplayName("updateBonusTerms - 다른_사용자의_행을_applyFrom으로_주면_404")
  void 다른_사용자의_행을_applyFrom으로_주면_404() {
    UUID id = UUID.randomUUID();
    IncomeHistory other =
        IncomeHistory.builder()
            .id(id)
            .userId(UUID.randomUUID())
            .grossSalary(1L)
            .effectiveDate(LocalDate.of(2025, 3, 1))
            .build();
    given(repository.findById(id)).willReturn(Optional.of(other));

    assertThatThrownBy(
            () -> service.updateBonusTerms(USER_ID, new BonusTermsRequest(id, true, null)))
        .isInstanceOf(IncomeNotFoundException.class);
  }

  @Test
  @DisplayName("create - 새_행은_직전_행의_상여_조건을_상속한다")
  void 새_행은_직전_행의_상여_조건을_상속한다() {
    LocalDate date = LocalDate.of(2026, 3, 1);
    IncomeHistory prev = row(LocalDate.of(2025, 3, 1));
    prev.applyBonusTerms(false, 6_000_000L);
    given(repository.existsByUserIdAndEffectiveDate(USER_ID, date)).willReturn(false);
    given(repository.findFirstByUserIdAndEffectiveDateBeforeOrderByEffectiveDateDesc(USER_ID, date))
        .willReturn(Optional.of(prev));
    given(repository.save(any(IncomeHistory.class))).willAnswer(i -> i.getArgument(0));

    IncomeResponse res =
        service.create(USER_ID, new IncomeRequest(65000000L, 4800000L, date, null));

    assertThat(res.bonusIncluded()).isFalse();
    assertThat(res.fixedBonusAnnual()).isEqualTo(6_000_000L);
  }

  @Test
  @DisplayName("create - 직전 행이 없으면 기본값이다")
  void 직전_행이_없으면_기본값() {
    LocalDate date = LocalDate.of(2024, 3, 1);
    given(repository.existsByUserIdAndEffectiveDate(USER_ID, date)).willReturn(false);
    given(repository.findFirstByUserIdAndEffectiveDateBeforeOrderByEffectiveDateDesc(USER_ID, date))
        .willReturn(Optional.empty());
    given(repository.save(any(IncomeHistory.class))).willAnswer(i -> i.getArgument(0));

    IncomeResponse res =
        service.create(USER_ID, new IncomeRequest(65000000L, 4800000L, date, null));

    assertThat(res.bonusIncluded()).isTrue();
    assertThat(res.fixedBonusAnnual()).isNull();
  }
}
