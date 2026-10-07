package com.carix.pipeline.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.pipeline.dto.explore.MonthlyOverview;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class MonthlyOverviewTest {

  @Autowired private JobPostingRepository repository;
  @Autowired private DataExploreService service;

  private void posting(
      String source, String id, String company, String family, String seen, String sampled) {
    JobPosting p =
        JobPosting.builder()
            .source(source)
            .externalId(id)
            .title("t" + id)
            .companyName(company)
            .jobFamily(family)
            .firstSeenMonth(seen)
            .lastSeenMonth(seen)
            .sampledMonth(sampled)
            .collectedAt(Instant.now())
            .build();
    repository.save(p);
  }

  @BeforeEach
  void seed() {
    for (int i = 0; i < 4; i++) {
      posting("JOBKOREA", "10" + i, "같은회사", "DEVELOPER", "202609", "202609"); // 상한 3 → 3
    }
    posting("JOBKOREA", "200", "다른회사", "DATA", "202609", "202609");
    posting("JOBKOREA", "300", "크롤", "DEVELOPER", "202609", null);
    posting("SARAMIN", "400", "사람인", "OTHER", "202609", null);
    posting("JOBKOREA", "500", "다른달", "AI", "202608", "202608");
    posting("WANTED", "w1", "원티드사", "AI", "202609", "202609");
    posting("WANTED", "w2", "원티드사", "AI", "202609", "202609");
    posting("WANTED", "w3", "원티드크롤", "DATA", "202609", null);
    repository.flush(); // JdbcTemplate 쿼리가 보게 한다
  }

  @Test
  @DisplayName("월별 표본·상한 적용 표본·정기 수집·직무별(상한 기준)을 센다")
  void monthlyCounts() {
    MonthlyOverview o = service.monthly("202608", "202609", null);

    MonthlyOverview.Month sep =
        o.months().stream().filter(m -> m.month().equals("202609")).findFirst().orElseThrow();
    assertThat(sep.jobkoreaSampled()).isEqualTo(5);
    assertThat(sep.jobkoreaSampledCapped()).isEqualTo(4);
    assertThat(sep.jobkoreaCrawled()).isEqualTo(1);
    assertThat(sep.saraminCrawled()).isEqualTo(1);
    assertThat(sep.sampledByFamily())
        .containsEntry("DEVELOPER", 3)
        .containsEntry("DATA", 1)
        .containsEntry("AI", 0)
        .containsEntry("OTHER", 0);
    assertThat(o.months())
        .extracting(MonthlyOverview.Month::month)
        .containsExactly("202608", "202609");
  }

  @Test
  @DisplayName("원티드 표본·상한 적용·정기 수집 열을 센다")
  void wantedColumns() {
    MonthlyOverview.Month sep = service.monthly("202609", "202609", null).months().get(0);
    assertThat(sep.wantedSampled()).isEqualTo(2);
    assertThat(sep.wantedSampledCapped()).isEqualTo(2);
    assertThat(sep.wantedCrawled()).isEqualTo(1);
  }

  @Test
  @DisplayName("sampleSource=WANTED 면 직무별 수가 원티드 표본 기준")
  void familyBySampleSource() {
    MonthlyOverview.Month sep = service.monthly("202609", "202609", "WANTED").months().get(0);
    assertThat(sep.sampledByFamily()).containsEntry("AI", 2).containsEntry("DEVELOPER", 0);
    assertThat(sep.jobkoreaSampled()).isEqualTo(5);
  }

  @Test
  @DisplayName("sampleSource 는 대소문자·앞뒤 공백을 무시한다")
  void sampleSourceIsNormalized() {
    MonthlyOverview.Month sep = service.monthly("202609", "202609", " wanted ").months().get(0);
    assertThat(sep.sampledByFamily()).containsEntry("AI", 2);
  }

  @Test
  @DisplayName("sampleSource 가 JOBKOREA·WANTED 가 아니면 400")
  void invalidSampleSource() {
    assertThatThrownBy(() -> service.monthly("202609", "202609", "SARAMIN"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("범위를 안 주면 서울 기준 이번 달 포함 최근 13개월")
  void defaultRange() {
    Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneOffset.UTC); // 서울 10월 1일
    assertThat(DataExploreService.currentMonth(clock)).isEqualTo(YearMonth.of(2026, 10));
    MonthlyOverview o = service.monthly(null, null, null);
    assertThat(o.months()).hasSize(13);
  }

  @Test
  @DisplayName("from>to, 36개월 초과, 형식 오류는 400")
  void invalidRange() {
    assertThatThrownBy(() -> service.monthly("202610", "202609", null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.monthly("202301", "202610", null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.monthly("2026-09", "202610", null))
        .isInstanceOf(ResponseStatusException.class);
  }
}
