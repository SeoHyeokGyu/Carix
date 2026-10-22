package com.carix.jobposting.wanted;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WantedFamilyResolverTest {

  private final WantedFamilyResolver resolver = new WantedFamilyResolver();

  @Test
  @DisplayName("하위 직군이 여럿이면 AI > DATA > DEVELOPER > OTHER")
  void priority() {
    assertThat(resolver.resolve(List.of(872, 1634), "x")).isEqualTo("AI");
    assertThat(resolver.resolve(List.of(872, 655), "x")).isEqualTo("DATA");
    assertThat(resolver.resolve(List.of(876, 872), "x")).isEqualTo("DEVELOPER");
    assertThat(resolver.resolve(List.of(876), "x")).isEqualTo("OTHER");
  }

  @Test
  @DisplayName("개발 하위 직군이라도 제목이 AI 면 AI, 직군이 없으면 DEVELOPER")
  void titleRule() {
    assertThat(resolver.resolve(List.of(872), "머신러닝 엔지니어")).isEqualTo("AI");
    assertThat(resolver.resolve(List.of(), "백엔드 개발자")).isEqualTo("DEVELOPER");
  }
}
