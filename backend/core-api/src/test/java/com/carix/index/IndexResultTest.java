package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import org.junit.jupiter.api.Test;

class IndexResultTest {

  @Test
  void 계산_불가는_값을_null_로_두고_0이나_100으로_채우지_않는다() {
    assertThat(PpiResult.notAvailable("202608").value()).isNull();
    assertThat(MviResult.notAvailable("202608").value()).isNull();
  }
}
