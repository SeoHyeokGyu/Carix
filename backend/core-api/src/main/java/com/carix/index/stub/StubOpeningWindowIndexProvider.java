package com.carix.index.stub;

import com.carix.index.OpeningWindowIndexProvider;
import com.carix.index.dto.OwiResult;
import java.util.UUID;

/** 계약만 있고 구현이 없는 동안 쓰는 자리표. 숫자를 지어내지 않고 계산 불가를 그대로 돌려준다. */
public class StubOpeningWindowIndexProvider implements OpeningWindowIndexProvider {

  @Override
  public OwiResult calculate(UUID userId, String baseYearMonth) {
    return OwiResult.notAvailable(baseYearMonth);
  }
}
