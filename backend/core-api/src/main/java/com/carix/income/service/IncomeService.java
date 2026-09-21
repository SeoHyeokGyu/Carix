package com.carix.income.service;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.dto.IncomeResponse;
import com.carix.income.exception.DuplicateEffectiveDateException;
import com.carix.income.exception.IncomeNotFoundException;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.YearMonths;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeService {

  private final IncomeHistoryRepository repository;
  private final SnapshotRecalculationService snapshotRecalculationService;

  public IncomeService(
      IncomeHistoryRepository repository,
      SnapshotRecalculationService snapshotRecalculationService) {
    this.repository = repository;
    this.snapshotRecalculationService = snapshotRecalculationService;
  }

  public List<IncomeResponse> getHistory(UUID userId) {
    return repository.findByUserIdOrderByEffectiveDateDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public IncomeResponse create(UUID userId, IncomeRequest request) {
    rejectInitial(request);
    if (repository.existsByUserIdAndEffectiveDate(userId, request.effectiveDate())) {
      throw new DuplicateEffectiveDateException(request.effectiveDate());
    }
    var saved =
        repository.save(
            new IncomeHistory(
                userId,
                request.grossSalary(),
                request.netSalary(),
                request.effectiveDate(),
                request.changeReason()));
    snapshotRecalculationService.recalculateSnapshot(
        userId, YearMonths.of(request.effectiveDate()));
    return toResponse(saved);
  }

  @Transactional
  public IncomeResponse update(UUID userId, UUID id, IncomeRequest request) {
    rejectInitial(request);
    var entity =
        repository
            .findById(id)
            .filter(e -> e.getUserId().equals(userId))
            .orElseThrow(() -> new IncomeNotFoundException(id));
    if (repository.existsByUserIdAndEffectiveDateAndIdNot(userId, request.effectiveDate(), id)) {
      throw new DuplicateEffectiveDateException(request.effectiveDate());
    }
    // 유효월을 앞으로 당기는 수정이면 옛 달부터 다시 계산해야 한다 — 새 달만 보면 그 사이 달이 옛 연봉으로 남는다.
    String fromYearMonth =
        YearMonths.earlier(
            YearMonths.of(entity.getEffectiveDate()), YearMonths.of(request.effectiveDate()));
    entity.update(
        request.grossSalary(),
        request.netSalary(),
        request.effectiveDate(),
        request.changeReason());
    snapshotRecalculationService.recalculateSnapshot(userId, fromYearMonth);
    return toResponse(entity);
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    var entity =
        repository
            .findById(id)
            .filter(e -> e.getUserId().equals(userId))
            .orElseThrow(() -> new IncomeNotFoundException(id));
    String fromYearMonth = YearMonths.of(entity.getEffectiveDate());
    repository.delete(entity);
    snapshotRecalculationService.recalculateSnapshot(userId, fromYearMonth);
  }

  /**
   * {@code INITIAL} 은 저장값이 아니라 서버가 가장 이른 행에 얹는 표시값이다. 요청으로 받으면 사용자가 고른 것처럼 컬럼에 남으므로 거절한다 (기능 명세
   * US-O3 — "첫 소득 행은 사용자에게 묻지 않는다").
   */
  private static void rejectInitial(IncomeRequest request) {
    if (request.changeReason() == IncomeChangeReasonEnum.INITIAL) {
      throw new IllegalArgumentException(
          "changeReason 에 INITIAL 을 지정할 수 없습니다. 최초 소득 행은 서버가 판정합니다.");
    }
  }

  private IncomeResponse toResponse(IncomeHistory e) {
    return new IncomeResponse(
        e.getId(),
        e.getGrossSalary(),
        e.getNetSalary() != null ? e.getNetSalary() : 0L,
        e.getEffectiveDate(),
        e.getChangeReason(),
        e.getChangeReason());
  }
}
