package com.carix.income.service;

import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.dto.IncomeResponse;
import com.carix.income.exception.DuplicateEffectiveDateException;
import com.carix.income.exception.IncomeNotFoundException;
import com.carix.income.repository.IncomeHistoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeService {

  private final IncomeHistoryRepository repository;

  public IncomeService(IncomeHistoryRepository repository) {
    this.repository = repository;
  }

  public List<IncomeResponse> getHistory(UUID userId) {
    return repository.findByUserIdOrderByEffectiveDateDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public IncomeResponse create(UUID userId, IncomeRequest request) {
    if (repository.existsByUserIdAndEffectiveDate(userId, request.effectiveDate())) {
      throw new DuplicateEffectiveDateException(request.effectiveDate());
    }
    var saved =
        repository.save(
            new IncomeHistory(
                userId, request.grossSalary(), request.netSalary(), request.effectiveDate()));
    // TODO: C의 recalculateSnapshot(userId, fromYm) 트리거 — 계약(§4) 확정 후 연결
    return toResponse(saved);
  }

  @Transactional
  public IncomeResponse update(UUID userId, UUID id, IncomeRequest request) {
    var entity =
        repository
            .findById(id)
            .filter(e -> e.getUserId().equals(userId))
            .orElseThrow(() -> new IncomeNotFoundException(id));
    if (repository.existsByUserIdAndEffectiveDateAndIdNot(userId, request.effectiveDate(), id)) {
      throw new DuplicateEffectiveDateException(request.effectiveDate());
    }
    entity.update(request.grossSalary(), request.netSalary(), request.effectiveDate());
    // TODO: recalculateSnapshot 트리거
    return toResponse(entity);
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    var entity =
        repository
            .findById(id)
            .filter(e -> e.getUserId().equals(userId))
            .orElseThrow(() -> new IncomeNotFoundException(id));
    repository.delete(entity);
    // TODO: recalculateSnapshot 트리거
  }

  private IncomeResponse toResponse(IncomeHistory e) {
    return new IncomeResponse(
        e.getId(),
        e.getGrossSalary(),
        e.getNetSalary() != null ? e.getNetSalary() : 0L,
        e.getEffectiveDate());
  }
}
