package com.carix.authservice.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

  @Test
  void 같은_입력은_같은_해시를_낸다_조회가_해시_일치로_이뤄지기_때문이다() {
    assertThat(TokenHasher.sha256Hex("abc")).isEqualTo(TokenHasher.sha256Hex("abc"));
  }

  @Test
  void 다른_입력은_다른_해시를_낸다() {
    assertThat(TokenHasher.sha256Hex("abc")).isNotEqualTo(TokenHasher.sha256Hex("abd"));
  }

  @Test
  void 결과는_64자_소문자_hex_다() {
    assertThat(TokenHasher.sha256Hex("abc")).matches("[0-9a-f]{64}");
  }

  @Test
  void 원본을_그대로_돌려주지_않는다() {
    String raw = "a".repeat(64);
    assertThat(TokenHasher.sha256Hex(raw)).isNotEqualTo(raw);
  }
}
