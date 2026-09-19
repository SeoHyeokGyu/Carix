package com.carix.snapshot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotRationaleTest {

  @Test
  @DisplayName("SnapshotRationale null 안전 불변 리스트 복사 및 기본값 처리")
  void testNullSafety() {
    SnapshotRationale rationaleNull =
        new SnapshotRationale(null, null, null, null, null, null, null, null, null, null, null);

    assertThat(rationaleNull.rulesApplied()).isEmpty();
    assertThat(rationaleNull.excludedSkills()).isEmpty();

    SnapshotRationale rationaleWithValues =
        new SnapshotRationale(
            List.of("Rule1"),
            "BELOW_MARKET_15_PLUS",
            List.of(),
            List.of(),
            List.of("SkillA"),
            LocalDate.now(),
            LocalDate.now(),
            "202607",
            "run-1",
            null,
            null);

    assertThat(rationaleWithValues.rulesApplied()).containsExactly("Rule1");
    assertThat(rationaleWithValues.excludedSkills()).containsExactly("SkillA");
  }
}
