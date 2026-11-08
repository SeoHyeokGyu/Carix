package com.carix.snapshot;

import com.carix.income.repository.IncomeHistoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 사용자 이력 전체를 다시 계산할 때의 시작월. 구매력 지수의 기준월이 최초 소득월이라 그보다 앞은 계산할 것이 없다. */
@Component
public class SnapshotRangeResolver {

  private final IncomeHistoryRepository incomeHistoryRepository;

  public SnapshotRangeResolver(IncomeHistoryRepository incomeHistoryRepository) {
    this.incomeHistoryRepository = incomeHistoryRepository;
  }

  /** 최초 소득 유효월({@code YYYYMM}). 소득이 없으면 현재월이다. */
  public String earliestFrom(UUID userId) {
    var incomes = incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);
    if (incomes.isEmpty()) {
      return YearMonths.current();
    }
    return YearMonths.of(incomes.get(incomes.size() - 1).getEffectiveDate());
  }
}
