package com.carix.snapshot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.dto.TopPriceDriver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotRationaleTest {

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
    // 소비 가중치 상태 도입 전 행 — 값이 없으면 뱃지를 그리지 않는다. 0 이나 기본 상태로 채우지 않는다.
    assertThat(rationale.expenseWeightStatus()).isNull();
    // 구매력 구성값 도입 전 행 — 추이 차트는 그 달 두 선을 그리지 않는다.
    assertThat(rationale.ppiComponents()).isNull();
  }

  @Test
  @DisplayName("topPriceDriver 는 금액 없이 왕복 직렬화된다")
  void topPriceDriver_roundTrips() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var driver =
        new TopPriceDriver("TRANSPORT", "교통 부담률 최대 상승", true, "202609", new BigDecimal("0.005"));
    var rationale =
        new SnapshotRationale(
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            "run-1",
            SnapshotRationale.IndexStatuses.unknown(),
            null,
            null,
            driver);

    String json = objectMapper.writeValueAsString(rationale);
    SnapshotRationale read = objectMapper.readValue(json, SnapshotRationale.class);

    assertThat(read.topPriceDriver()).isEqualTo(driver);
  }

  @Test
  @DisplayName("b22 이전 openingWindow 구 필드(medianWindowDays 등)가 포함된 JSON 도 오류 없이 역직렬화된다")
  void legacyOpeningWindowJson_ignoresOldFields() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    String legacyJson =
        """
        {
          "rulesApplied": [],
          "indexStatuses": {"ppi": "OK", "mvi": "OK", "owi": "OK"},
          "openingWindow": {
            "peerSampleSize": 124,
            "deadlineSampleSize": 86,
            "medianWindowDays": 34,
            "openEndedRatePercent": 21.50,
            "fallbackLevel": 3,
            "appliedConditions": "서울 전체 / 경력 MID / 회사규모 무관",
            "marketMedianWindowDays": 29
          }
        }
        """;

    SnapshotRationale rationale = objectMapper.readValue(legacyJson, SnapshotRationale.class);

    assertThat(rationale.openingWindow()).isNotNull();
    assertThat(rationale.openingWindow().peerSampleSize()).isEqualTo(124);
    assertThat(rationale.openingWindow().standardError()).isNull();
    assertThat(rationale.openingWindow().recentFromMonth()).isNull();
    assertThat(rationale.openingWindow().recentToMonth()).isNull();
    assertThat(rationale.openingWindow().windowsFullyCovered()).isNull();
    assertThat(rationale.openingWindow().maxFallbackLevel()).isNull();
    assertThat(rationale.openingWindow().recentSharePercent()).isNull();
    assertThat(rationale.openingWindow().baselineSharePercent()).isNull();
  }

  @Test
  @DisplayName("산식 버전이 없는 이전 행은 1 로 읽고 새 행은 기록된 버전을 읽는다")
  void ppiFormulaVersion_이전_행은_1() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    SnapshotRationale legacy = mapper.readValue("{\"runId\":\"r\"}", SnapshotRationale.class);
    SnapshotRationale current =
        mapper.readValue(
            mapper.writeValueAsString(
                new SnapshotRationale(
                    null, null, null, null, null, null, null, null, "r", null, null, null, null,
                    "2")),
            SnapshotRationale.class);

    assertThat(legacy.ppiFormulaVersion()).isNull();
    assertThat(legacy.ppiFormulaVersionOrLegacy()).isEqualTo("1");
    assertThat(current.ppiFormulaVersionOrLegacy()).isEqualTo("2");
  }

  @Test
  @DisplayName("미지의 필드가 있는 행도 읽는다 — 이전 버전으로 되돌려도 새 행이 깨지지 않는다")
  void 미지의_필드는_무시하고_읽는다() throws Exception {
    // Hibernate 의 JSON 포맷 매퍼는 기본 ObjectMapper 라 FAIL_ON_UNKNOWN_PROPERTIES 가 켜져 있다
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    SnapshotRationale rationale =
        mapper.readValue("{\"runId\":\"r\",\"someFutureField\":\"x\"}", SnapshotRationale.class);

    assertThat(rationale.runId()).isEqualTo("r");
  }
}
