package com.carix.index.stub;

import com.carix.index.CurrentSalary;
import com.carix.index.CurrentSalaryProvider;
import java.util.Optional;
import java.util.UUID;

/** 계약만 있고 구현이 없는 동안 쓰는 자리표. 금액을 지어내지 않고 없음을 그대로 돌려준다. */
public class StubCurrentSalaryProvider implements CurrentSalaryProvider {

  @Override
  public Optional<CurrentSalary> currentGrossSalary(UUID userId, String baseYearMonth) {
    return Optional.empty();
  }
}
