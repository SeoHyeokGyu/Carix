package com.carix.jobposting.jumpit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JumpitFamilyResolverTest {

  private final JumpitFamilyResolver resolver = new JumpitFamilyResolver();

  @Test
  @DisplayName("여러 분류면 AI > DATA > DEVELOPER > OTHER 순으로 고른다")
  void priority() {
    assertThat(resolver.resolve(List.of("서버/백엔드 개발자", "인공지능/머신러닝"), "x")).isEqualTo("AI");
    assertThat(resolver.resolve(List.of("서버/백엔드 개발자", "DBA"), "x")).isEqualTo("DATA");
    assertThat(resolver.resolve(List.of("개발 PM", "프론트엔드 개발자"), "x")).isEqualTo("DEVELOPER");
    assertThat(resolver.resolve(List.of("기술지원"), "x")).isEqualTo("OTHER");
  }

  @Test
  @DisplayName("분류가 없으면 제목 규칙, 못 가르면 DEVELOPER")
  void fallsBackToTitle() {
    assertThat(resolver.resolve(List.of(), "머신러닝 엔지니어")).isEqualTo("AI");
    assertThat(resolver.resolve(List.of(), "Software Engineer")).isEqualTo("DEVELOPER");
  }
}
