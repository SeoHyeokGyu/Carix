package com.carix.dashboard.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.enums.IndexStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
}
