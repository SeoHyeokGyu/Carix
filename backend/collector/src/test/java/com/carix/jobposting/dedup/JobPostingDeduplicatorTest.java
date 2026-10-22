package com.carix.jobposting.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.JobPosting;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobPostingDeduplicatorTest {

  private final CompanyNormalizer companyNormalizer = new CompanyNormalizer();
  private final TitleNormalizer titleNormalizer = new TitleNormalizer();
  private final JobPostingDeduplicator deduplicator =
      new JobPostingDeduplicator(companyNormalizer, titleNormalizer, null);

  @Test
  @DisplayName("deduplicate - 잡코리아와 사람인의 동일 회사 유사 공고를 동일한 dedup_group_id로 묶는다")
  void deduplicate_groupsCrossSourcePostings() {
    // Given: 잡코리아와 사람인에 등록된 동일 회사 공고 2건
    JobPosting p1 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .companyName("(주)카카오")
            .title("[경력] 서버 백엔드 개발자 (Java/Spring)")
            .collectedAt(Instant.now())
            .build();

    JobPosting p2 =
        JobPosting.builder()
            .source("SARAMIN")
            .externalId("sr-1")
            .companyName("주식회사 카카오")
            .title("카카오 Java Spring 백엔드 개발자 채용")
            .collectedAt(Instant.now())
            .build();

    // 다른 회사 공고 1건
    JobPosting p3 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-2")
            .companyName("네이버")
            .title("AI 머신러닝 연구원")
            .collectedAt(Instant.now())
            .build();

    List<JobPosting> postings = List.of(p1, p2, p3);

    // When
    int multiGroups = deduplicator.deduplicate(postings);

    // Then
    assertThat(multiGroups).isEqualTo(1);
    assertThat(p1.getDedupGroupId()).isEqualTo(p2.getDedupGroupId());
    assertThat(p1.getDedupRuleVersion()).isEqualTo("v1");
    assertThat(p3.getDedupGroupId()).isNotEqualTo(p1.getDedupGroupId());
  }

  @Test
  @DisplayName("deduplicate - 회사명이 비어있는 경우 및 제목 키워드 부분집합 매칭")
  void deduplicate_emptyCompanyAndSubsetKeywords() {
    JobPosting p1 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-sub-1")
            .companyName("테스트기업")
            .title("백엔드 서버 개발자")
            .collectedAt(Instant.now())
            .build();

    JobPosting p2 =
        JobPosting.builder()
            .source("SARAMIN")
            .externalId("sr-sub-2")
            .companyName("테스트기업")
            .title("백엔드 개발자") // p2 키워드가 p1의 진부분집합
            .collectedAt(Instant.now())
            .build();

    JobPosting pEmptyComp =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-empty-3")
            .companyName("") // 회사명 빈값
            .title("단독 공고")
            .collectedAt(Instant.now())
            .build();

    int matched = deduplicator.deduplicate(List.of(p1, p2, pEmptyComp));
    assertThat(matched).isEqualTo(1);
    assertThat(p1.getDedupGroupId()).isEqualTo(p2.getDedupGroupId());
  }
}
