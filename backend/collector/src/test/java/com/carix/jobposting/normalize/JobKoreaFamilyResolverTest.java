package com.carix.jobposting.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.backfill.JobKoreaItCodes;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobKoreaFamilyResolverTest {

  private final JobKoreaFamilyResolver resolver =
      new JobKoreaFamilyResolver(new JobKoreaItCodes(Set.of("4010000167")));

  private static String page(String codes, String recommendedJobType) {
    return "<script>self.__next_f.push([1,\"\\\"category\\\":{\\\"jobClassificationCodes\\\":["
        + codes
        + "]},\\\"jobTypeName\\\":\\\""
        + recommendedJobType
        + "\\\"\"])</script>";
  }

  @Test
  @DisplayName("자기 공고 직무 코드가 IT 면 제목으로 직무군을 가른다 — 추천 공고의 jobTypeName 은 보지 않는다")
  void ignoresRecommendedJobTypeName() {
    String html = page("\\\"4010000167\\\"", "AI엔지니어");

    assertThat(resolver.resolve(html, "Java 백엔드 개발자")).isEqualTo("DEVELOPER");
    assertThat(resolver.resolve(html, "데이터 엔지니어")).isEqualTo("DATA");
  }

  @Test
  @DisplayName("IT 코드가 없거나 상세페이지가 없으면 OTHER 다")
  void otherWithoutItCode() {
    assertThat(resolver.resolve(page("\\\"4010000000\\\"", "백엔드개발자"), "백엔드 개발자"))
        .isEqualTo("OTHER");
    assertThat(resolver.resolve(null, "백엔드 개발자")).isEqualTo("OTHER");
  }
}
