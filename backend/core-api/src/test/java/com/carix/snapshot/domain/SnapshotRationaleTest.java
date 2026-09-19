package com.carix.snapshot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

  /**
   * production {@code core.index_snapshot} 에는 {@code indexStatuses}/{@code openingWindow} 도입 이전에 쓰인
   * 9필드짜리 rationale JSON 이 있다. 레코드로 만들어 접은 값이 아니라, 그 시절 그대로의 JSON 문자열을 직접 역직렬화해 컴팩트 생성자의 폴딩이 실제로 그
   * 경로를 지키는지 검증한다.
   */
  @Test
  @DisplayName("indexStatuses/openingWindow 도입 전 9필드 JSON 도 역직렬화되어 unknown 으로 접힌다")
  void 아홉_필드짜리_옛_rationale_JSON_이_역직렬화된다() throws Exception {
    String legacyRationaleJson =
        """
        {
          "rulesApplied": ["연봉 격차 15% 이상"],
          "salaryGapBucket": "BELOW_MARKET_15_PLUS",
          "sourceCoverages": [],
          "skillContributions": [],
          "excludedSkills": ["CustomSkillX"],
          "cpiAsOf": "2026-07-01",
          "aggregateAsOf": "2026-07-01",
          "wageStatVintage": "202606",
          "runId": "20260701093000-000"
        }
        """;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    SnapshotRationale rationale =
        objectMapper.readValue(legacyRationaleJson, SnapshotRationale.class);

    assertThat(rationale.indexStatuses()).isEqualTo(SnapshotRationale.IndexStatuses.unknown());
    assertThat(rationale.openingWindow()).isNull();
  }
}
