package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcDataHealthReaderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("rationale 에서 지수 상태와 규칙 문구만 읽는다")
  void parsesStatusesAndRules() {
    String json =
        "{\"indexStatuses\":{\"ppi\":\"INSUFFICIENT_HISTORY\",\"mvi\":\"OK\",\"owi\":\"OK\"},\"rulesApplied\":[\"시장가치 불확실\"],\"salaryGapBucket\":\"NEAR_MARKET\"}";
    var s =
        JdbcDataHealthReader.parseSnapshot(
            "6fa4914e-xxxx", "202610", "1.3.0", "STAY", json, mapper);
    assertThat(s.ppiStatus()).isEqualTo("INSUFFICIENT_HISTORY");
    assertThat(s.mviStatus()).isEqualTo("OK");
    assertThat(s.rulesApplied()).containsExactly("시장가치 불확실");
  }

  @Test
  @DisplayName("깨졌거나 비어 있는 rationale 은 상태 null 이다")
  void brokenRationale() {
    var s = JdbcDataHealthReader.parseSnapshot("u", "202610", "1.3.0", "STAY", "{oops", mapper);
    assertThat(s.ppiStatus()).isNull();
    assertThat(s.rulesApplied()).isEmpty();
  }
}
