package com.carix.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketAggregateFrameTest {

  private JobPostingRepository jobPostingRepository;
  private MarketAggregateRepository marketAggregateRepository;
  private MarketAggregateService service;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    marketAggregateRepository = mock(MarketAggregateRepository.class);
    service = new MarketAggregateService(jobPostingRepository, marketAggregateRepository);
  }

  private static JobPosting saramin(
      String id, String first, String last, LocalDate deadline, String... via) {
    JobPosting p =
        JobPosting.builder()
            .source("SARAMIN")
            .externalId(id)
            .jobFamily("DEVELOPER")
            .experienceMin(3)
            .firstSeenMonth(first)
            .lastSeenMonth(last)
            .postedEndDate(deadline)
            .collectedAt(Instant.now())
            .build();
    for (String v : via) {
      p.addCollectedVia(v);
    }
    return p;
  }

  /** 원천 전체 행(스킬·차원 없음)의 공고 수. 행이 없으면 empty. */
  private OptionalInt total(List<MarketAggregate> rows, String source) {
    return rows.stream()
        .filter(r -> source.equals(r.getSource()) && r.getSkillId() == null)
        .filter(r -> "ALL".equals(r.getJobFamily()))
        .mapToInt(MarketAggregate::getPostingCount)
        .max();
  }

  @SuppressWarnings("unchecked")
  private List<MarketAggregate> aggregate(String month, JobPosting... postings) {
    when(jobPostingRepository.findAll()).thenReturn(List.of(postings));
    service.aggregateMarketDataForMonth(month);
    ArgumentCaptor<List<MarketAggregate>> captor = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository, atLeastOnce()).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("SARAMIN 행은 검색 경로만, JUMPIT 행은 점핏 경로만 세고 두 경로 공고는 양쪽에 든다")
  void frameRowsSplitByCollectionPath() {
    var rows =
        aggregate(
            "202609",
            saramin("s1", "202609", "202609", null, CollectedVia.SARAMIN_SEARCH),
            saramin("j1", "202609", "202609", null, CollectedVia.JUMPIT),
            saramin(
                "b1", "202609", "202609", null, CollectedVia.SARAMIN_SEARCH, CollectedVia.JUMPIT));
    assertThat(total(rows, "SARAMIN")).hasValue(2);
    assertThat(total(rows, "JUMPIT")).hasValue(2);
    assertThat(total(rows, "ALL")).hasValue(3);
  }

  /** 저장된 모든 행. 저장이 없으면 빈 목록. */
  @SuppressWarnings("unchecked")
  private List<MarketAggregate> savedRows(String month, JobPosting... postings) {
    when(jobPostingRepository.findAll()).thenReturn(List.of(postings));
    service.aggregateMarketDataForMonth(month);
    ArgumentCaptor<List<MarketAggregate>> captor = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository, atLeast(0)).saveAll(captor.capture());
    return captor.getAllValues().stream().flatMap(List::stream).toList();
  }

  @Test
  @DisplayName("마지막 관측월 뒤 마감 전 달은 프레임 행에 세지 않는다(유량)")
  void frameRowsDoNotExtendPastLastSeenToDeadline() {
    var rows =
        savedRows(
            "202609",
            saramin(
                "s1", "202608", "202608", LocalDate.of(2026, 9, 20), CollectedVia.SARAMIN_SEARCH));
    assertThat(rows).noneMatch(r -> "SARAMIN".equals(r.getSource()));
    assertThat(rows).noneMatch(r -> "ALL".equals(r.getSource()));
  }

  @Test
  @DisplayName("전체 재집계는 마감월이 아니라 관측월만 집계한다")
  void extractedMonthsIgnoreDeadline() {
    when(jobPostingRepository.findAll())
        .thenReturn(
            List.of(
                saramin(
                    "s1",
                    "202608",
                    "202608",
                    LocalDate.of(2026, 9, 15),
                    CollectedVia.SARAMIN_SEARCH)));
    service.aggregateAllMarketData();
    verify(marketAggregateRepository).deleteByBaseYearMonth("202608");
    verify(marketAggregateRepository, never()).deleteByBaseYearMonth("202609");
  }

  @Test
  @DisplayName("게시일이 일러도 첫 관측월 전 달은 프레임 행에 세지 않는다")
  void notActiveBeforeFirstSeen() {
    var p =
        JobPosting.builder()
            .source("SARAMIN")
            .externalId("j1")
            .jobFamily("DEVELOPER")
            .experienceMin(3)
            .firstSeenMonth("202610")
            .lastSeenMonth("202610")
            .postedStartDate(LocalDate.of(2026, 9, 1))
            .postedEndDate(LocalDate.of(2026, 10, 30))
            .collectedAt(Instant.now())
            .build();
    p.addCollectedVia(CollectedVia.JUMPIT);
    assertThat(savedRows("202609", p)).noneMatch(r -> "JUMPIT".equals(r.getSource()));
  }

  @Test
  @DisplayName("표본 원천 행은 그대로다")
  void sampledRowsUnchanged() {
    JobPosting jk =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("1")
            .jobFamily("DEVELOPER")
            .experienceMin(3)
            .firstSeenMonth("202609")
            .lastSeenMonth("202609")
            .companyName("가")
            .collectedAt(Instant.now())
            .build();
    jk.markSampled("202609");
    var rows = aggregate("202609", jk);
    assertThat(total(rows, "JOBKOREA")).hasValue(1);
    assertThat(total(rows, "SARAMIN")).isEmpty();
    assertThat(total(rows, "JUMPIT")).isEmpty();
  }
}
