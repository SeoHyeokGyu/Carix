package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcDataHealthReaderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("지수 상태 조각과 규칙 문구 조각을 읽는다")
  void parsesStatusesAndRules() {
    var s =
        JdbcDataHealthReader.parseSnapshot(
            "6fa4914e-xxxx",
            "202610",
            "1.3.0",
            "STAY",
            "{\"ppi\":\"INSUFFICIENT_HISTORY\",\"mvi\":\"OK\"}",
            "[\"시장가치 불확실\"]",
            mapper);
    assertThat(s.ppiStatus()).isEqualTo("INSUFFICIENT_HISTORY");
    assertThat(s.mviStatus()).isEqualTo("OK");
    assertThat(s.rulesApplied()).containsExactly("시장가치 불확실");
  }

  @Test
  @DisplayName("깨진 조각은 상태 null, 규칙 빈 목록이다")
  void brokenFragments() {
    var s =
        JdbcDataHealthReader.parseSnapshot(
            "u", "202610", "1.3.0", "STAY", "{oops", "[oops", mapper);
    assertThat(s.ppiStatus()).isNull();
    assertThat(s.mviStatus()).isNull();
    assertThat(s.rulesApplied()).isEmpty();
  }

  @Test
  @DisplayName("비어 있는 조각은 상태 null, 규칙 빈 목록이다")
  void nullFragments() {
    var s = JdbcDataHealthReader.parseSnapshot("u", "202610", "1.3.0", "STAY", null, null, mapper);
    assertThat(s.ppiStatus()).isNull();
    assertThat(s.mviStatus()).isNull();
    assertThat(s.rulesApplied()).isEmpty();
  }
}
