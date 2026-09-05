package com.carix.jobposting.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TitleNormalizerTest {

  private final TitleNormalizer normalizer = new TitleNormalizer();

  @Test
  @DisplayName("extractKeywords - 괄호 태그 및 불용어를 제거하고 핵심 키워드만 추출한다")
  void extractKeywords_cleansTagsAndStopwords() {
    Set<String> keywords = normalizer.extractKeywords("[신입/경력] 백엔드 개발자 채용 (Java/Spring Boot)");

    assertThat(keywords).contains("백엔드", "개발자", "java", "spring", "boot");
    assertThat(keywords).doesNotContain("채용", "신입", "경력");
  }

  @Test
  @DisplayName("calculateJaccardSimilarity - 유사한 제목의 높은 유사도를 산출한다")
  void calculateJaccardSimilarity_calculatesCorrectly() {
    Set<String> t1 = normalizer.extractKeywords("카카오페이 백엔드 개발자 (Java, Kotlin)");
    Set<String> t2 = normalizer.extractKeywords("[카카오페이] Java/Kotlin 백엔드 서버 엔지니어 모집");

    double similarity = normalizer.calculateJaccardSimilarity(t1, t2);
    assertThat(similarity).isGreaterThan(0.4);
  }

  @Test
  @DisplayName("extractKeywords - null, 공백, 특수문자 전용 문자열 처리")
  void extractKeywords_edgeCases() {
    assertThat(normalizer.extractKeywords(null)).isEmpty();
    assertThat(normalizer.extractKeywords("   ")).isEmpty();
    assertThat(normalizer.extractKeywords("!@#$%^&*()")).isEmpty();
    // 1글자 단어는 필터링됨
    assertThat(normalizer.extractKeywords("a b c d e")).isEmpty();
  }

  @Test
  @DisplayName("calculateJaccardSimilarity - 둘 다 비어있으면 1.0, 한쪽만 비어있으면 0.0")
  void calculateJaccardSimilarity_emptyCases() {
    assertThat(normalizer.calculateJaccardSimilarity(Set.of(), Set.of())).isEqualTo(1.0);
    assertThat(normalizer.calculateJaccardSimilarity(Set.of("java"), Set.of())).isEqualTo(0.0);
    assertThat(normalizer.calculateJaccardSimilarity(Set.of(), Set.of("spring"))).isEqualTo(0.0);
  }
}
