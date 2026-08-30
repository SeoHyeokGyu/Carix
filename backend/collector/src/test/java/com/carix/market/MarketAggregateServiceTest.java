package com.carix.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketAggregateServiceTest {

  private JobPostingRepository jobPostingRepository;
  private MarketAggregateRepository marketAggregateRepository;
  private MarketAggregateService marketAggregateService;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    marketAggregateRepository = mock(MarketAggregateRepository.class);
    marketAggregateService =
        new MarketAggregateService(jobPostingRepository, marketAggregateRepository);
  }

  @Test
  @DisplayName("aggregateMarketDataForMonth - 직군·경력별 공고수, 스킬 수요율, 중위연봉을 정확하게 계산하여 저장한다")
  void aggregateMarketData_calculatesCorrectly() {
    // Given
    Skill javaSkill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").category("BACKEND").build();

    JobPosting p1 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .experienceMin(3)
            .salaryMin(4000L)
            .salaryMax(6000L)
            .collectedAt(Instant.now())
            .build();
    p1.addSkill(javaSkill);

    JobPosting p2 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-2")
            .jobFamily("BACKEND")
            .experienceMin(2)
            .salaryMin(5000L)
            .salaryMax(7000L)
            .collectedAt(Instant.now())
            .build();
    p2.addSkill(javaSkill);

    when(jobPostingRepository.findAll()).thenReturn(List.of(p1, p2));

    // When
    int count = marketAggregateService.aggregateMarketDataForMonth("202608");

    // Then
    assertThat(count).isGreaterThan(0);
    verify(marketAggregateRepository).deleteByBaseYearMonth("202608");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MarketAggregate>> captor = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository).saveAll(captor.capture());

    List<MarketAggregate> saved = captor.getValue();
    assertThat(saved).isNotEmpty();

    // BACKEND & JUNIOR 슬라이스 검증
    MarketAggregate backendJunior =
        saved.stream()
            .filter(
                m ->
                    "ALL".equals(m.getSource())
                        && "BACKEND".equals(m.getJobFamily())
                        && "JUNIOR".equals(m.getExperienceBracket())
                        && m.getSkillId() == null)
            .findFirst()
            .orElseThrow();

    assertThat(backendJunior.getPostingCount()).isEqualTo(2);
    assertThat(backendJunior.getSalaryMedian()).isEqualTo(6000L); // (5000 + 6000) p50
  }

  @Test
  @DisplayName("aggregateMarketData(request) - 연도 구간 및 전체 요청 시 모든 대상 월에 대해 멱등 집계를 실행한다")
  void aggregateMarketData_withRequestOptions() {
    // Given
    JobPosting p =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .firstSeenMonth("202501")
            .lastSeenMonth("202503")
            .collectedAt(Instant.now())
            .build();
    when(jobPostingRepository.findAll()).thenReturn(List.of(p));

    // When: 전체(all=true) 집계 요청
    int count =
        marketAggregateService.aggregateMarketData(
            new com.carix.pipeline.dto.MarketAggregateRequest(null, null, null, null, true));

    // Then: 202501, 202503에 대해 delete 및 save 실행
    assertThat(count).isGreaterThan(0);
    verify(marketAggregateRepository).deleteByBaseYearMonth("202501");
    verify(marketAggregateRepository).deleteByBaseYearMonth("202503");
  }
}
