package com.carix.jobposting.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobKoreaItCodesTest {

  private final JobKoreaItCodes codes = new JobKoreaItCodes(Set.of("4010000167", "4010000177"));

  @Test
  @DisplayName("코드 하나라도 IT 카테고리 코드면 IT 공고다")
  void anyItCode() {
    assertThat(codes.isIt(List.of("4010000000", "4010000177"))).isTrue();
    assertThat(codes.isIt(List.of("4010000000"))).isFalse();
    assertThat(codes.isIt(List.of())).isFalse();
  }

  @Test
  @DisplayName("동봉된 코드 목록을 읽는다")
  void loadsBundled() {
    assertThat(JobKoreaItCodes.bundled().size()).isPositive();
  }
}
