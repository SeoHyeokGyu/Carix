package com.carix.income.service;

import com.carix.income.domain.PerformanceBonus;
import com.carix.income.dto.PerformanceBonusRequest;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.exception.IncomeNotFoundException;
import com.carix.income.exception.InvalidPaidMonthException;
import com.carix.income.repository.PerformanceBonusRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceBonusService {

  private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

  private final PerformanceBonusRepository repository;
  private final Clock clock;

  public PerformanceBonusService(PerformanceBonusRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public List<PerformanceBonusResponse> list(UUID userId) {
    return repository.findByUserIdOrderByPaidMonthDesc(userId).stream()
        .map(PerformanceBonusResponse::from)
        .toList();
  }

  @Transactional
  public PerformanceBonusResponse create(UUID userId, PerformanceBonusRequest request) {
    requireReachedMonth(request.paidMonth());
    var saved =
        repository.save(new PerformanceBonus(userId, request.paidMonth(), request.amount()));
    return PerformanceBonusResponse.from(saved);
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    var bonus =
        repository.findByIdAndUserId(id, userId).orElseThrow(() -> new IncomeNotFoundException(id));
    repository.delete(bonus);
  }

  /** 받은 성과급만 기록한다 — 월이 실재하고 현재 월을 넘지 않아야 한다. */
  private void requireReachedMonth(String paidMonth) {
    YearMonth month;
    try {
      month = YearMonth.parse(paidMonth, YYYYMM);
    } catch (DateTimeParseException e) {
      throw new InvalidPaidMonthException("지급월의 월은 01~12 여야 합니다.");
    }
    if (month.isAfter(YearMonth.now(clock))) {
      throw new InvalidPaidMonthException("아직 오지 않은 달에는 성과급을 기록할 수 없습니다.");
    }
  }
}
