package com.carix.jobposting.programmers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgrammersFamilyResolverTest {

  private final ProgrammersFamilyResolver resolver = new ProgrammersFamilyResolver();

  @Test
  @DisplayName("여러 분류면 AI > DATA > DEVELOPER > OTHER 순으로 판정한다")
  void priority() {
    assertThat(resolver.resolve(List.of("서버 개발자", "인공지능"), "백엔드 개발")).isEqualTo("AI");
    assertThat(resolver.resolve(List.of("서버 개발자", "DBA"), "백엔드 개발")).isEqualTo("DATA");
    assertThat(resolver.resolve(List.of("개발 PM", "프론트엔드"), "웹 개발")).isEqualTo("DEVELOPER");
    assertThat(resolver.resolve(List.of("기술지원", "기획"), "기획자")).isEqualTo("OTHER");
  }

  @Test
  @DisplayName("분류가 없으면 제목 기반으로 판정하고, 기본값은 DEVELOPER 다")
  void fallbackToTitle() {
    assertThat(resolver.resolve(List.of(), "머신러닝 엔지니어 채용")).isEqualTo("AI");
    assertThat(resolver.resolve(List.of(), "빅데이터 파이프라인 개발자")).isEqualTo("DATA");
    assertThat(resolver.resolve(List.of(), "백엔드 스프링 개발자")).isEqualTo("DEVELOPER");
    assertThat(resolver.resolve(List.of(), "서비스 기획자")).isEqualTo("OTHER");
  }

  @Test
  @DisplayName("카테고리가 null이거나 제목이 null/일반 텍스트인 경우의 분기를 처리한다")
  void handlesNullAndUnmatchedTitles() {
    // 1. 카테고리 null + 제목 개발 키워드 없음 -> 기본 DEVELOPER
    assertThat(resolver.resolve(null, "열정적인 인재를 채용합니다")).isEqualTo("DEVELOPER");

    // 2. 카테고리 null + 제목 null -> 기본 DEVELOPER
    assertThat(resolver.resolve(null, null)).isEqualTo("DEVELOPER");

    // 3. 비개발 타이틀 키워드 매칭 -> OTHER
    assertThat(resolver.resolve(null, "프로덕트 디자이너 채용")).isEqualTo("OTHER");
    assertThat(resolver.resolve(null, "인사총무(HR) 담당자")).isEqualTo("OTHER");
    assertThat(resolver.resolve(null, "B2B 영업 마케팅 매니저")).isEqualTo("OTHER");
  }
}
