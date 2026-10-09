package com.carix.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

  @Test
  @DisplayName("마지막 수집 뒤 마감 전 달은 ALL 이 비어도 프레임 행이 남는다")
  void frameRowsSurviveWhenAllRowEmpty() {
    var rows =
        aggregate(
            "202609",
            saramin(
                "s1", "202608", "202608", LocalDate.of(2026, 9, 20), CollectedVia.SARAMIN_SEARCH));
    assertThat(total(rows, "SARAMIN")).hasValue(1);
    assertThat(total(rows, "ALL")).isEmpty();
  }

  @Test
  @DisplayName("마감일 연장은 마지막 관측월 + 2개월까지다")
  void deadlineExtensionCappedAtTwoMonths() {
    var p =
        saramin("s1", "202608", "202608", LocalDate.of(2026, 12, 31), CollectedVia.SARAMIN_SEARCH);
    assertThat(service.isActiveInFrameMonth(p, "202610")).isTrue();
    assertThat(service.isActiveInFrameMonth(p, "202611")).isFalse();
  }

  @Test
  @DisplayName("게시일이 일러도 첫 관측월 전 달은 활동이 아니다")
  void notActiveBeforeFirstSeen() {
    var p =
        JobPosting.builder()
            .source("SARAMIN")
            .firstSeenMonth("202610")
            .lastSeenMonth("202610")
            .postedStartDate(LocalDate.of(2026, 9, 1))
            .postedEndDate(LocalDate.of(2026, 10, 30))
            .build();
    p.addCollectedVia(CollectedVia.JUMPIT);
    assertThat(service.isActiveInFrameMonth(p, "202609")).isFalse();
    assertThat(service.isActiveInFrameMonth(p, "202610")).isTrue();
  }

  @Test
  @DisplayName("마감일이 없으면 마지막 관측월까지다")
  void noDeadlineUsesLastSeen() {
    var p = saramin("s1", "202608", "202609", null, CollectedVia.SARAMIN_SEARCH);
    assertThat(service.isActiveInFrameMonth(p, "202609")).isTrue();
    assertThat(service.isActiveInFrameMonth(p, "202610")).isFalse();
  }

  @Test
  @DisplayName("전체 재집계는 마감일로 늘어난 달도 집계한다")
  void extractedMonthsIncludeDeadlineExtension() {
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
    verify(marketAggregateRepository).deleteByBaseYearMonth("202609");
  }

  @Test
  @DisplayName("마감일 연장이 당월(UTC) 뒤 달을 만들지 않는다")
  void noFutureMonthFromDeadline() {
    YearMonth now = YearMonth.now(ZoneOffset.UTC);
    String nowStr = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
    String next = now.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
    when(jobPostingRepository.findAll())
        .thenReturn(
            List.of(
                saramin(
                    "s1",
                    nowStr,
                    nowStr,
                    now.plusMonths(1).atDay(10),
                    CollectedVia.SARAMIN_SEARCH)));
    service.aggregateAllMarketData();
    verify(marketAggregateRepository, never()).deleteByBaseYearMonth(next);
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
