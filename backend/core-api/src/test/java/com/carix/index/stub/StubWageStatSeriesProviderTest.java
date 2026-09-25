package com.carix.index.stub;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.dto.WageStatSeries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StubWageStatSeriesProviderTest {

  @Test
  @DisplayName("스텁 프로바이더는 빈 포인트 목록을 반환한다")
  void findSeries_returnsEmpty() {
    StubWageStatSeriesProvider stub = new StubWageStatSeriesProvider();
    WageStatSeries series = stub.findSeries("DEVELOPER", "MID");

    assertThat(series).isNotNull();
    assertThat(series.jobFamily()).isEqualTo("DEVELOPER");
    assertThat(series.experienceBracket()).isEqualTo("MID");
    assertThat(series.points()).isEmpty();
    assertThat(series.hasValidKosisGrowthInterval()).isFalse();
  }
}
