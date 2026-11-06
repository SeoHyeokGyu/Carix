package com.carix.index.stub;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.RecordedIncomeChange;
import com.carix.index.dto.RecordedYearIncome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 계약만 있고 구현이 없는 동안 쓰는 자리표. 금액을 지어내지 않고 없음을 그대로 돌려준다. */
public class StubCurrentSalaryProvider implements CurrentSalaryProvider {

  @Override
  public Optional<CurrentSalary> currentGrossSalary(UUID userId, String baseYearMonth) {
    return Optional.empty();
  }

  @Override
  public List<RecordedYearIncome> recordedYearIncomes(UUID userId) {
    return List.of();
  }

  @Override
  public List<RecordedIncomeChange> recordedIncomeChanges(UUID userId) {
    return List.of();
  }
}
