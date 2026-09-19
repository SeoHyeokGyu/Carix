package com.carix.dashboard.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.DecisionSignalCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지수 응답 DTO 의 계산 불가 표현 검증.
 *
 * <p>계약(API 규격 §5.1)은 "계산 불가는 예외가 아니라 {@link IndexStatus} 로 돌려준다. 값은 {@code null} 이고 0 이나 100 으로
 * 채우지 않는다"고 규정한다. DTO 가 primitive 를 쓰면 이 계약을 표현할 수 없어 스텁을 걷어내도 {@code 0.0} 이 나간다.
 */
class IndexNullabilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("IndexDetail - 계산 불가면 value 가 null 이고 indexStatus 로 이유를 전달한다")
  void indexDetail_계산불가() throws Exception {
    DashboardSummaryResponse.IndexDetail detail =
        new DashboardSummaryResponse.IndexDetail(
            null, null, null, IndexStatus.NOT_AVAILABLE.name(), null);

    String json = objectMapper.writeValueAsString(detail);

    assertThat(json).contains("\"value\":null");
    assertThat(json).contains("\"indexStatus\":\"NOT_AVAILABLE\"");
    assertThat(json).doesNotContain("\"value\":0.0");
  }

  @Test
  @DisplayName("IndexDetail - 표본 부족은 값 없이 INSUFFICIENT_SAMPLE 로 나간다")
  void indexDetail_표본부족() throws Exception {
    DashboardSummaryResponse.IndexDetail detail =
        new DashboardSummaryResponse.IndexDetail(
            null, null, null, IndexStatus.INSUFFICIENT_SAMPLE.name(), null);

    assertThat(objectMapper.writeValueAsString(detail))
        .contains("\"indexStatus\":\"INSUFFICIENT_SAMPLE\"");
  }

  @Test
  @DisplayName("TrendPoint - 결측월은 지수와 금액이 모두 null 이라 차트가 갭으로 그린다")
  void trendPoint_결측월() throws Exception {
    TrendListResponse.TrendPoint point =
        new TrendListResponse.TrendPoint("2026-03", null, null, null, null, null, null, null);

    String json = objectMapper.writeValueAsString(point);

    assertThat(json).contains("\"purchasingPowerIndex\":null");
    assertThat(json).contains("\"openingWindowIndex\":null");
    assertThat(json).contains("\"nominalIncome\":null");
    assertThat(json).doesNotContain(":0.0");
    assertThat(json).doesNotContain(":0,");
  }

  @Test
  @DisplayName("TrendListResponse - 계열별 상태를 응답 최상위에 싣는다")
  void trendList_계열별상태() throws Exception {
    TrendListResponse response =
        new TrendListResponse(
            List.of(),
            IndexStatus.NOT_AVAILABLE.name(),
            IndexStatus.INSUFFICIENT_SAMPLE.name(),
            IndexStatus.OK.name());

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("\"purchasingPowerStatus\":\"NOT_AVAILABLE\"");
    assertThat(json).contains("\"marketValueStatus\":\"INSUFFICIENT_SAMPLE\"");
    assertThat(json).contains("\"openingWindowStatus\":\"OK\"");
  }

  @Test
  @DisplayName("SnapshotPoint - 그 달에 계산이 불가했으면 지수가 null 로 남는다")
  void snapshotPoint_계산불가월() throws Exception {
    SnapshotHistoryResponse.SnapshotPoint point =
        new SnapshotHistoryResponse.SnapshotPoint(
            "202603",
            null,
            null,
            null,
            "INSUFFICIENT_DATA",
            DecisionSignalCode.INSUFFICIENT_DATA.label(),
            "20260301093000-000",
            "1.2.0",
            new SnapshotHistoryResponse.SnapshotRationale(
                List.of(), List.of(), LocalDate.of(2026, 3, 1)));

    String json = objectMapper.writeValueAsString(point);

    assertThat(json).contains("\"purchasingPowerIndex\":null");
    assertThat(json).contains("\"marketValueIndex\":null");
    assertThat(json).contains("\"openingWindowIndex\":null");
    assertThat(json).doesNotContain(":0.0");
  }
}
