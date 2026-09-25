package com.carix.index.stub;

import com.carix.index.WageStatSeriesProvider;
import com.carix.index.dto.WageStatSeries;
import java.util.List;

/**
 * 공식 임금통계 연도 시계열 제공자 스텁 (Member B 미구현 시 대비).
 *
 * <p>실제 구현 빈이 존재하면 비활성화됩니다.
 */
public class StubWageStatSeriesProvider implements WageStatSeriesProvider {

  @Override
  public WageStatSeries findSeries(String jobFamily, String experienceBracket) {
    return new WageStatSeries(jobFamily, experienceBracket, "ALL", List.of());
  }
}
