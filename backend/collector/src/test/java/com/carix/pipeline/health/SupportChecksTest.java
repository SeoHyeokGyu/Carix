package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SupportChecksTest {

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private static HealthVerdict verdict(Map<String, Instant> latest) {
    var data =
        new DataHealthSnapshot(null, null, null, null, null, null, latest, List.of(), null, null);
    return SupportChecks.group(data, NOW, HealthThresholds.defaults())
        .checks()
        .getFirst()
        .verdict();
  }

  @Test
  @DisplayName("정기 수집이 35일 이내면 정상, 넘으면 주의, 기록이 없으면 판정 불가")
  void crawlFreshness() {
    assertThat(verdict(Map.of("JOBKOREA", NOW.minus(Duration.ofDays(35)))))
        .isEqualTo(HealthVerdict.OK);
    assertThat(verdict(Map.of("JOBKOREA", NOW.minus(Duration.ofDays(36)))))
        .isEqualTo(HealthVerdict.WARN);
    assertThat(verdict(Map.of())).isEqualTo(HealthVerdict.UNKNOWN);
  }
}
