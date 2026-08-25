package com.carix.jobposting.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanyNormalizerTest {

  private final CompanyNormalizer normalizer = new CompanyNormalizer();

  @Test
  @DisplayName("normalize - 법인 접두/접미사를 깔끔하게 제거하고 소문자화한다")
  void normalize_cleansCorpPrefixes() {
    assertThat(normalizer.normalize("(주)카카오")).isEqualTo("카카오");
    assertThat(normalizer.normalize("주식회사 카카오")).isEqualTo("카카오");
    assertThat(normalizer.normalize("카카오(주)")).isEqualTo("카카오");
    assertThat(normalizer.normalize("NAVER Corp.")).isEqualTo("naver");
    assertThat(normalizer.normalize("쿠팡 Inc.")).isEqualTo("쿠팡");
    assertThat(normalizer.normalize("(유)토스")).isEqualTo("토스");
  }

  @Test
  @DisplayName("normalize - null 및 빈 문자열 처리")
  void normalize_handlesNullAndEmpty() {
    assertThat(normalizer.normalize(null)).isEmpty();
    assertThat(normalizer.normalize("   ")).isEmpty();
  }
}
