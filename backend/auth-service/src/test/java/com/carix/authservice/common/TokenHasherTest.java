package com.carix.authservice.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

  @Test
  void 같은_입력은_같은_해시를_낸다_조회가_해시_일치로_이뤄지기_때문이다() {
    assertThat(TokenHasher.sha256Hex("abc")).isEqualTo(TokenHasher.sha256Hex("abc"));
  }
}
