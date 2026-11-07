package com.carix.income.service;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.BonusTermsRequest;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.dto.IncomeResponse;
import com.carix.income.exception.DuplicateEffectiveDateException;
import com.carix.income.exception.IncomeNotFoundException;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.snapshot.SnapshotRecalculationRequested;
import com.carix.snapshot.YearMonths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeService {

  private final IncomeHistoryRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  public IncomeService(
      IncomeHistoryRepository repository, ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  public List<IncomeResponse> getHistory(UUID userId) {
    List<IncomeHistory> rows = repository.findByUserIdOrderByEffectiveDateDesc(userId);
    // 정렬이 effective_date DESC 라 마지막 원소가 최소 유효일이다 — 파생에 쿼리를 더 쓰지 않는다.
    // 인덱스로 고르는 것은 단위 테스트의 엔티티가 아직 id 를 갖지 않기 때문이기도 하다.
    return IntStream.range(0, rows.size())
        .mapToObj(i -> toResponse(rows.get(i), i == rows.size() - 1))
        .toList();
  }

  @Transactional
  public IncomeResponse create(UUID userId, IncomeRequest request) {
    rejectInitial(request);
    if (repository.existsByUserIdAndEffectiveDate(userId, request.effectiveDate())) {
      throw new DuplicateEffectiveDateException(request.effectiveDate());
    }
    var created =
        new IncomeHistory(
            userId,
            request.grossSalary(),
            request.netSalary(),
            request.effectiveDate(),
            request.changeReason());
    // 상여 조건은 연봉 행부터 이어지는 조건이라 새 행은 바로 앞 행의 것을 물려받는다.
    repository
        .findFirstByUserIdAndEffectiveDateBeforeOrderByEffectiveDateDesc(
            userId, request.effectiveDate())
        .ifPresent(
            prev -> created.applyBonusTerms(prev.isBonusIncluded(), prev.getFixedBonusAnnual()));
    var saved = repository.save(created);
    eventPublisher.publishEvent(
        new SnapshotRecalculationRequested(userId, YearMonths.of(request.effectiveDate())));
    return toResponse(saved, isEarliest(userId, request.effectiveDate()));
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
    eventPublisher.publishEvent(new SnapshotRecalculationRequested(userId, fromYearMonth));
    return toResponse(entity, isEarliestExcluding(userId, request.effectiveDate(), id));
  }

  /** {@code applyFromIncomeId} 행과 그 유효일 이후의 모든 행에 같은 상여 조건을 쓴다. */
  @Transactional
  public List<IncomeResponse> updateBonusTerms(UUID userId, BonusTermsRequest request) {
    var from =
        repository
            .findById(request.applyFromIncomeId())
            .filter(e -> e.getUserId().equals(userId))
            .orElseThrow(() -> new IncomeNotFoundException(request.applyFromIncomeId()));
    boolean bonusIncluded = request.bonusIncluded();
    // 들어 있음이면 따로 받는 금액이 없다 — 컨트롤러가 막지만 서비스도 null 로 고정한다.
    Long fixedBonusAnnual = bonusIncluded ? null : request.fixedBonusAnnual();
    repository.findByUserIdOrderByEffectiveDateDesc(userId).stream()
        .filter(e -> !e.getEffectiveDate().isBefore(from.getEffectiveDate()))
        .forEach(e -> e.applyBonusTerms(bonusIncluded, fixedBonusAnnual));
    eventPublisher.publishEvent(
        new SnapshotRecalculationRequested(userId, YearMonths.of(from.getEffectiveDate())));
    return getHistory(userId);
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
    eventPublisher.publishEvent(new SnapshotRecalculationRequested(userId, fromYearMonth));
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

  /** UNIQUE (user_id, effective_date) 가 동률을 막아 최소 유효일 행은 항상 하나다. */
  private boolean isEarliest(UUID userId, LocalDate effectiveDate) {
    return !repository.existsByUserIdAndEffectiveDateBefore(userId, effectiveDate);
  }

  /** 수정 경로는 자기 행을 뺀다 — 저장소 주석 참조. */
  private boolean isEarliestExcluding(UUID userId, LocalDate effectiveDate, UUID id) {
    return !repository.existsByUserIdAndEffectiveDateBeforeAndIdNot(userId, effectiveDate, id);
  }

  /**
   * {@code changeReason} 만 컬럼값과 갈린다 — 최소 유효일 행이면 {@code INITIAL} 을 얹는다. {@code
   * selectedChangeReason} 은 컬럼 원값 그대로라 편집 폼이 그것을 채우고 되돌려 보낼 수 있다.
   */
  private IncomeResponse toResponse(IncomeHistory e, boolean isEarliest) {
    return new IncomeResponse(
        e.getId(),
        e.getGrossSalary(),
        e.getNetSalary() != null ? e.getNetSalary() : 0L,
        e.getEffectiveDate(),
        isEarliest ? IncomeChangeReasonEnum.INITIAL : e.getChangeReason(),
        e.getChangeReason(),
        e.isBonusIncluded(),
        e.getFixedBonusAnnual());
  }
}
